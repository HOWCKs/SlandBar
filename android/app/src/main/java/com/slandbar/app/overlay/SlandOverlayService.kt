package com.slandbar.app.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import androidx.compose.ui.platform.ComposeView
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.ViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.slandbar.app.MainActivity
import com.slandbar.app.R
import com.slandbar.app.core.ActionExecutor
import com.slandbar.app.core.MediaController
import com.slandbar.app.core.OverlayManager
import com.slandbar.app.core.SlandPrefs
import com.slandbar.app.core.TimerController
import com.slandbar.app.core.model.HapticProfile
import com.slandbar.app.core.model.Settings
import com.slandbar.app.core.model.ShortcutAction
import com.slandbar.app.core.model.GestureType
import android.provider.Settings as AndroidSettings
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Serviço em primeiro plano que desenha a barra flutuante e o painel de
 * widgets por cima de qualquer aplicativo (janela SYSTEM_ALERT_WINDOW).
 */
class SlandOverlayService : LifecycleService() {

    companion object {
        private const val TAG = "SlandOverlay"
        private const val NOTIFICATION_ID = 42
        const val CHANNEL_ID = "slandbar_overlay"
        const val ACTION_START = "com.slandbar.app.action.START"
        const val ACTION_STOP = "com.slandbar.app.action.STOP"

        @Volatile
        var isRunning = false
            private set

        /** Chamado pelo serviço de acessibilidade quando o teclado abre/fecha. */
        @JvmStatic
        fun onKeyboardVisibilityChanged(visible: Boolean) {
            instance?.handleKeyboard(visible)
        }

        @Volatile
        private var instance: SlandOverlayService? = null
    }

    // ---- Estado compartilhado com a UI (Compose) ----

    data class ToggleState(
        val torchOn: Boolean = false,
        val dndOn: Boolean = false,
        val rotationAuto: Boolean = true,
        val ringerMode: Int = AudioManager.RINGER_MODE_NORMAL,
        val brightness: Int = 128,
        val volume: Int = 10,
        val volumeMax: Int = 15
    )

    private val _toggles = MutableStateFlow(ToggleState())
    val toggles: StateFlow<ToggleState> = _toggles

    private val _battery = MutableStateFlow(100)
    val battery: StateFlow<Int> = _battery

    private val _barPosition = MutableStateFlow(0 to 0)
    val barPosition: StateFlow<Pair<Int, Int>> = _barPosition

    private val _expanded = MutableStateFlow(false)
    val expanded: StateFlow<Boolean> = _expanded

    // ---- Componentes ----

    private lateinit var prefs: SlandPrefs
    private lateinit var windowManager: WindowManager
    private val media = MediaController(this)
    private lateinit var timer: TimerController
    private lateinit var executor: ActionExecutor

    private var pillView: ComposeView? = null
    private var pillParams: WindowManager.LayoutParams? = null
    private var panelView: ComposeView? = null
    private var panelParams: WindowManager.LayoutParams? = null

    private var settings: Settings = Settings()
    private var autoHideJob: Job? = null
    private var dragging = false
    private var downX = 0f
    private var downY = 0f
    private var startX = 0
    private var startY = 0
    private var lastTapTime = 0L
    private var pendingSingleTap: Runnable? = null
    private val longPressRunnable = Runnable { onLongPress() }
    private val mainHandler = Handler(Looper.getMainLooper())

    private var screenW = 0
    private var screenH = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = SlandPrefs(this)
        timer = TimerController(lifecycleScope)
        executor = ActionExecutor(
            context = this,
            onExpand = { showPanel() },
            onSystemChanged = { refreshToggles() }
        )
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        registerBatteryReceiver()
        val (w, h) = screenSize()
        screenW = w
        screenH = h

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch { prefs.settings.collect { s ->
                    settings = s
                    applySettings()
                } }
                launch { media.state.collect { } }
                launch { timer.state.collect { } }
            }
        }
        media.start()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
        }
        if (!AndroidSettings.canDrawOverlays(this)) {
            stopSelf()
            return START_NOT_STICKY
        }
        startInForeground()
        isRunning = true

        // O serviço pode ter sido iniciado pelo boot, pela notificação ou pelo
        // app: só mantém ativo se overlay_enabled estiver ligado.
        lifecycleScope.launch {
            prefs.settings.collect { s ->
                if (!s.overlayEnabled) {
                    stopSelf()
                } else if (pillView == null) {
                    showPill()
                }
            }
        }
        return START_STICKY
    }

    private fun startInForeground() {
        createNotificationChannel()
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val stopIntent = PendingIntent.getService(
            this, 1,
            Intent(this, SlandOverlayService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(getString(R.string.overlay_notif_title))
            .setContentText(getString(R.string.overlay_notif_text))
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, getString(R.string.overlay_stop_action), stopIntent)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    // ---- Janela da barra (pill) ----

    private suspend fun showPill() {
        if (pillView != null) return
        val saved = prefs.barPosition()
        val x = if (saved.first != 0 || saved.second != 0) saved.first
        else defaultX()
        val y = if (saved.second != 0) saved.second else screenH / 3

        val view = ComposeView(this).apply {
            setContent {
                FloatingBarUi.PillRoot(
                    settings = settings,
                    battery = _battery,
                    toggles = _toggles,
                    media = media.state
                )
            }
        }
        ViewTreeLifecycleOwner.set(view, this)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            this.x = x
            this.y = y
        }
        view.setOnTouchListener { _, event -> handleTouch(event) }
        windowManager.addView(view, lp)
        pillView = view
        pillParams = lp
        _barPosition.value = x to y
        scheduleAutoHide()
    }

    private fun defaultX(): Int {
        val pillW = pillSizePx().first
        return screenW - pillW - marginPx()
    }

    private fun pillSizePx(): Pair<Int, Int> {
        val d = resources.displayMetrics.density
        val s = settings.barSize.scale
        return ((118 * s * d).toInt()) to ((40 * s * d).toInt())
    }

    private fun marginPx(): Int = (10 * resources.displayMetrics.density).toInt()

    // ---- Gestos da barra ----

    private fun handleTouch(event: MotionEvent): Boolean {
        val view = pillView ?: return false
        if (_expanded.value) return false // painel aberto: não rouba toques
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = false
                downX = event.rawX
                downY = event.rawY
                startX = pillParams?.x ?: 0
                startY = pillParams?.y ?: 0
                mainHandler.removeCallbacks(longPressRunnable)
                mainHandler.postDelayed(longPressRunnable, 420)
                view.alpha = 1f
                resetAutoHide()
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                val slop = ViewConfiguration.get(this).scaledTouchSlop
                if (!dragging && (abs(dx) > slop || abs(dy) > slop)) {
                    dragging = true
                    mainHandler.removeCallbacks(longPressRunnable)
                    vibrate(settings.hapticProfile, weak = true)
                }
                if (dragging) {
                    pillParams?.let { lp ->
                        lp.x = (startX + dx).toInt().coerceIn(-pillSizePx().first / 2, screenW - pillSizePx().first / 2)
                        lp.y = (startY + dy).toInt().coerceIn(0, screenH - pillSizePx().second)
                        windowManager.updateViewLayout(view, lp)
                        _barPosition.value = lp.x to lp.y
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                mainHandler.removeCallbacks(longPressRunnable)
                val dx = event.rawX - downX
                val dy = event.rawY - downY
                if (dragging) {
                    snapToEdge()
                    savePosition()
                } else if (dy < -splashPx()) {
                    // Deslizou para cima
                    executeGesture(GestureType.SWIPE_UP)
                } else if (dy > splashPx()) {
                    // Deslizou para baixo (mostra atalho alternativo: nada por padrão)
                    executeGesture(GestureType.SWIPE_UP)
                } else {
                    onTap()
                }
                resetAutoHide()
            }
            MotionEvent.ACTION_CANCEL -> {
                mainHandler.removeCallbacks(longPressRunnable)
            }
        }
        return true
    }

    private fun splashPx(): Int = (70 * resources.displayMetrics.density).toInt()

    private fun onTap() {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 300) {
            // Toque duplo
            lastTapTime = 0
            pendingSingleTap?.let { mainHandler.removeCallbacks(it) }
            pendingSingleTap = null
            executeGesture(GestureType.DOUBLE_TAP)
            return
        }
        lastTapTime = now
        pendingSingleTap = Runnable { executeGesture(GestureType.TAP) }
        mainHandler.postDelayed(pendingSingleTap!!, 260)
    }

    private fun onLongPress() {
        pendingSingleTap?.let { mainHandler.removeCallbacks(it) }
        pendingSingleTap = null
        executeGesture(GestureType.LONG_PRESS)
    }

    private fun executeGesture(gesture: GestureType) {
        val action = when (gesture) {
            GestureType.TAP -> settings.gestureTap
            GestureType.DOUBLE_TAP -> settings.gestureDoubleTap
            GestureType.LONG_PRESS -> settings.gestureLongPress
            GestureType.SWIPE_UP -> settings.gestureSwipeUp
        }
        if (action == ShortcutAction.NONE) return
        vibrate(settings.hapticProfile, weak = false)
        executor.execute(action)
    }

    // ---- Painel expandido ----

    private fun showPanel() {
        if (panelView != null || pillView == null) return
        refreshToggles()
        _expanded.value = true
        autoHideJob?.cancel()

        val view = ComposeView(this).apply {
            setContent {
                FloatingBarUi.PanelRoot(
                    settings = settings,
                    barPosition = _barPosition,
                    screenSize = screenW to screenH,
                    toggles = _toggles,
                    battery = _battery,
                    media = media.state,
                    timer = timer.state,
                    actions = FloatingBarUi.PanelActions(
                        onCollapse = { collapsePanel() },
                        onClose = {
                            collapsePanel()
                            lifecycleScope.launch { prefs.setOverlayEnabled(false) }
                            stopSelf()
                        },
                        onToggleTorch = { executor.execute(ShortcutAction.FLASHLIGHT) },
                        onToggleDnd = { executor.execute(ShortcutAction.DND) },
                        onToggleRotation = { executor.execute(ShortcutAction.ROTATION) },
                        onCycleRinger = { executor.execute(ShortcutAction.RINGER) },
                        onMediaPlayPause = { media.playPause() },
                        onMediaNext = { media.next() },
                        onMediaPrev = { media.previous() },
                        onTimerStartStop = { timer.startStop() },
                        onTimerReset = { timer.reset() },
                        onTimerMode = { mode, seconds -> timer.setMode(mode, seconds) },
                        onBrightness = { v -> setBrightness(v) },
                        onVolume = { v -> setVolume(v) },
                        onShortcut = { item -> executor.execute(item) }
                    )
                )
            }
        }
        ViewTreeLifecycleOwner.set(view, this)

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 0
            y = 0
        }
        windowManager.addView(view, lp)
        panelView = view
        panelParams = lp
        pillView?.alpha = 0f
    }

    private fun collapsePanel() {
        panelView?.let { runCatching { windowManager.removeView(it) } }
        panelView = null
        panelParams = null
        _expanded.value = false
        pillView?.alpha = 1f
        scheduleAutoHide()
    }

    // ---- Ajustes de sistema (toggles + sliders) ----

    private fun refreshToggles() {
        val nm = getSystemService(NotificationManager::class.java)
        val am = getSystemService(AudioManager::class.java)
        val brightness = runCatching {
            AndroidSettings.System.getInt(contentResolver, AndroidSettings.System.SCREEN_BRIGHTNESS)
        }.getOrDefault(128)
        _toggles.value = ToggleState(
            torchOn = ActionExecutor.Torch.on,
            dndOn = nm.currentInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL,
            rotationAuto = AndroidSettings.System.getInt(
                contentResolver, AndroidSettings.System.ACCELEROMETER_ROTATION, 1
            ) == 1,
            ringerMode = am.ringerMode,
            brightness = brightness,
            volume = am.getStreamVolume(AudioManager.STREAM_MUSIC),
            volumeMax = am.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        )
    }

    private fun setBrightness(value: Int) {
        if (!AndroidSettings.System.canWrite(this)) return
        AndroidSettings.System.putInt(contentResolver, AndroidSettings.System.SCREEN_BRIGHTNESS, value)
        AndroidSettings.System.putInt(contentResolver, AndroidSettings.System.SCREEN_BRIGHTNESS_MODE, AndroidSettings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
        _toggles.value = _toggles.value.copy(brightness = value)
    }

    private fun setVolume(value: Int) {
        val am = getSystemService(AudioManager::class.java)
        am.setStreamVolume(AudioManager.STREAM_MUSIC, value, 0)
        _toggles.value = _toggles.value.copy(volume = value)
    }

    // ---- Auto-ocultar, bordas, posição ----

    private fun applySettings() {
        // Tamanho/opacidade: a janela é WRAP_CONTENT — ajustamos via alpha e
        // a escala é aplicada no conteúdo Compose (PillRoot lê `settings`).
        pillView?.alpha = settings.opacity
        if (_expanded.value) return
        scheduleAutoHide()
    }

    private fun scheduleAutoHide() {
        autoHideJob?.cancel()
        if (!settings.autoHide || _expanded.value) return
        autoHideJob = lifecycleScope.launch {
            delay(settings.autoHideSeconds * 1000L)
            pillView?.let { v ->
                v.animate().alpha(0.3f).setDuration(500).start()
            }
        }
    }

    private fun resetAutoHide() {
        pillView?.alpha = settings.opacity
        scheduleAutoHide()
    }

    private fun snapToEdge() {
        val lp = pillParams ?: return
        val (w, _) = pillSizePx()
        val targetX = if (lp.x < screenW / 2) marginPx() else screenW - w - marginPx()
        val targetY = lp.y.coerceIn(0, screenH - pillSizePx().second)
        pillView?.let { v ->
            v.animate().setDuration(180).start()
        }
        lp.x = targetX
        lp.y = targetY
        runCatching { windowManager.updateViewLayout(pillView!!, lp) }
        _barPosition.value = targetX to targetY
    }

    private fun savePosition() {
        val lp = pillParams ?: return
        lifecycleScope.launch { prefs.setBarPosition(lp.x, lp.y) }
    }

    private fun handleKeyboard(visible: Boolean) {
        if (visible && _expanded.value) collapsePanel()
    }

    // ---- Diversos ----

    private fun vibrate(profile: HapticProfile, weak: Boolean) {
        if (!settings.hapticsEnabled) return
        com.slandbar.app.core.Haptics.vibrate(this, if (weak) HapticProfile.SOFT else profile)
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val battery = registerReceiver(null, filter) ?: return
        val level = battery.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = battery.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        if (level >= 0 && scale > 0) {
            _battery.value = (level * 100 / scale)
        }
    }

    private fun screenSize(): Pair<Int, Int> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        val bounds = windowManager.currentWindowMetrics.bounds
        bounds.width() to bounds.height()
    } else {
        @Suppress("DEPRECATION")
        val dm = resources.displayMetrics
        dm.widthPixels to dm.heightPixels
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply { description = getString(R.string.overlay_channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    override fun onDestroy() {
        isRunning = false
        if (instance === this) instance = null
        autoHideJob?.cancel()
        runCatching { panelView?.let { windowManager.removeView(it) } }
        runCatching { pillView?.let { windowManager.removeView(it) } }
        panelView = null
        pillView = null
        media.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // (fim da classe)
}
