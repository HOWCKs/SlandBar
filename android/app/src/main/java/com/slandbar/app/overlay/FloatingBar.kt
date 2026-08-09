package com.slandbar.app.overlay

import android.graphics.drawable.Drawable
import android.net.Uri
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoNotDisturb
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FlashlightOff
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage
import com.slandbar.app.R
import com.slandbar.app.core.MediaState
import com.slandbar.app.core.TimerController
import com.slandbar.app.core.TimerState
import com.slandbar.app.core.model.Settings
import com.slandbar.app.core.model.ShortcutAction
import com.slandbar.app.core.model.ShortcutItem
import com.slandbar.app.core.model.TimerMode
import com.slandbar.app.core.model.WidgetId
import com.slandbar.app.overlay.SlandOverlayService.ToggleState
import com.slandbar.app.ui.SlandTheme
import com.slandbar.app.ui.parseAccentColor
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.StateFlow
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.min
import kotlin.math.roundToInt

/** Toda a UI flutuante do app (pílula + painel de widgets), em Jetpack Compose. */
object FloatingBarUi {

    data class PanelActions(
        val onCollapse: () -> Unit,
        val onClose: () -> Unit,
        val onToggleTorch: () -> Unit,
        val onToggleDnd: () -> Unit,
        val onToggleRotation: () -> Unit,
        val onCycleRinger: () -> Unit,
        val onMediaPlayPause: () -> Unit,
        val onMediaNext: () -> Unit,
        val onMediaPrev: () -> Unit,
        val onTimerStartStop: () -> Unit,
        val onTimerReset: () -> Unit,
        val onTimerMode: (TimerMode, Int) -> Unit,
        val onBrightness: (Int) -> Unit,
        val onVolume: (Int) -> Unit,
        val onShortcut: (ShortcutItem) -> Unit
    )

    // ---------------------------------------------------------------- Pill

    @Composable
    fun PillRoot(
        settings: Settings,
        battery: StateFlow<Int>,
        toggles: StateFlow<ToggleState>,
        media: StateFlow<MediaState>
    ) {
        val dark = settings.themeMode != com.slandbar.app.core.model.ThemeMode.LIGHT
        SlandTheme(dark = dark, useDynamic = settings.useDynamicColors, accent = parseAccentColor(settings.accentHex)) {
            val batt by battery.collectAsState()
            val t by toggles.collectAsState()
            val m by media.collectAsState()
            val time by produceState(initialValue = "") {
                while (true) {
                    value = timeNow()
                    delay(1000)
                }
            }
            val scale = settings.barSize.scale

            Row(
                modifier = Modifier
                    .height((38 * scale).dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xE6162034))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(50))
                    .padding(horizontal = (12 * scale).dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = time,
                    color = Color.White,
                    fontSize = (11.5 * scale).sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 0.5.sp
                )
                Spacer(Modifier.width((7 * scale).dp))
                Box(
                    Modifier
                        .width(1.dp)
                        .height((14 * scale).dp)
                        .background(Color.White.copy(alpha = 0.18f))
                )
                Spacer(Modifier.width((7 * scale).dp))
                Icon(
                    Icons.Filled.BatteryFull,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.size((13 * scale).dp)
                )
                Text(
                    text = "$batt%",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = (10.5 * scale).sp
                )
                if (t.torchOn) {
                    Spacer(Modifier.width((6 * scale).dp))
                    Icon(
                        Icons.Filled.FlashlightOn,
                        contentDescription = null,
                        tint = Color(0xFFFFC94D),
                        modifier = Modifier.size((12 * scale).dp)
                    )
                }
                if (m.active) {
                    Spacer(Modifier.width((6 * scale).dp))
                    Icon(
                        Icons.Filled.MusicNote,
                        contentDescription = null,
                        tint = Color(0xFF7DD3FC),
                        modifier = Modifier.size((12 * scale).dp)
                    )
                }
                Spacer(Modifier.width((8 * scale).dp))
                Icon(
                    Icons.Filled.DragHandle,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.45f),
                    modifier = Modifier.size((14 * scale).dp)
                )
            }
        }
    }

    // ------------------------------------------------------------- Panel

    @Composable
    fun PanelRoot(
        settings: Settings,
        barPosition: StateFlow<Pair<Int, Int>>,
        screenSize: Pair<Int, Int>,
        toggles: StateFlow<ToggleState>,
        battery: StateFlow<Int>,
        media: StateFlow<MediaState>,
        timer: StateFlow<TimerState>,
        actions: PanelActions
    ) {
        val dark = settings.themeMode != com.slandbar.app.core.model.ThemeMode.LIGHT
        SlandTheme(dark = dark, useDynamic = settings.useDynamicColors, accent = parseAccentColor(settings.accentHex)) {
            val t by toggles.collectAsState()
            val m by media.collectAsState()
            val tm by timer.collectAsState()
            val pos by barPosition.collectAsState()
            val density = LocalDensity.current

            var cardSize by remember { mutableStateOf(IntSize.Zero) }
            val cardWidth = with(density) {
                val maxW = (screenSize.first - 24.dp.toPx()).toFloat()
                min(360.dp.toPx(), maxW).toDp()
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
                    .pointerInput(Unit) {
                        // Toque fora do cartão → recolhe
                        androidx.compose.foundation.gestures.detectTapGestures {
                            actions.onCollapse()
                        }
                    }
            ) {
                Card(
                    modifier = Modifier
                        .width(cardWidth)
                        .offset {
                            val dx = ((screenSize.first - cardSize.width) / 2).coerceAtLeast(0)
                            val aboveBar = pos.second - cardSize.height - dp(8).toPx().toInt()
                            val belowBar = pos.second + dp(56).toPx().toInt()
                            val maxY = (screenSize.second - cardSize.height - dp(16).toPx().toInt()).coerceAtLeast(0)
                            val dy = if (aboveBar > 0) aboveBar else belowBar
                            IntOffset(dx, dy.coerceAtMost(maxY))
                        }
                        .onSizeChanged { cardSize = it }
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { /* consome toques para não recolher ao interagir */ },
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f)
                    ),
                    elevation = CardDefaults.cardElevation(defaultElevation = 24.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .padding(20.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        PanelHeader(actions)
                        if (WidgetId.CLOCK in settings.widgets) ClockSection()
                        if (WidgetId.TOGGLES in settings.widgets) TogglesSection(t, actions)
                        if (WidgetId.SHORTCUTS in settings.widgets) ShortcutsSection(settings, actions)
                        if (WidgetId.MEDIA in settings.widgets && settings.premium) MediaSection(m, actions)
                        if (WidgetId.MEDIA in settings.widgets && !settings.premium) PremiumHint()
                        if (WidgetId.TIMER in settings.widgets) TimerSection(tm, actions)
                        if (WidgetId.BRIGHTNESS in settings.widgets) BrightnessSection(t, actions)
                        if (WidgetId.VOLUME in settings.widgets && settings.premium) VolumeSection(t, actions)
                        if (WidgetId.VOLUME in settings.widgets && !settings.premium) PremiumHint()
                    }
                }
            }
        }
    }

    @Composable
    private fun PanelHeader(actions: PanelActions) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    "SlandBar",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    DateFormat.getDateInstance(DateFormat.LONG, Locale.getDefault()).format(Date()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = actions.onCollapse) {
                Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Recolher")
            }
            IconButton(onClick = actions.onClose) {
                Icon(Icons.Filled.Close, contentDescription = "Fechar SlandBar")
            }
        }
    }

    @Composable
    private fun ClockSection() {
        val time by produceState(initialValue = timeNow()) {
            while (true) {
                value = timeNow(seconds = true)
                delay(1000)
            }
        }
        Text(
            text = time,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp
        )
        Spacer(Modifier.height(4.dp))
    }

    @Composable
    private fun TogglesSection(t: ToggleState, actions: PanelActions) {
        SectionTitle("Controles rápidos")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            ToggleButton(
                icon = if (t.torchOn) Icons.Filled.FlashlightOn else Icons.Filled.FlashlightOff,
                label = "Lanterna",
                active = t.torchOn,
                onClick = actions.onToggleTorch
            )
            ToggleButton(
                icon = Icons.Filled.DoNotDisturb,
                label = "DND",
                active = t.dndOn,
                onClick = actions.onToggleDnd
            )
            ToggleButton(
                icon = Icons.Filled.ScreenRotation,
                label = "Rotação",
                active = t.rotationAuto,
                onClick = actions.onToggleRotation
            )
            ToggleButton(
                icon = Icons.Filled.Notifications,
                label = "Som",
                active = t.ringerMode == android.media.AudioManager.RINGER_MODE_NORMAL,
                onClick = actions.onCycleRinger
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    @Composable
    private fun ToggleButton(
        icon: ImageVector,
        label: String,
        active: Boolean,
        onClick: () -> Unit
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onClick),
                shape = CircleShape,
                color = if (active) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = label,
                        tint = if (active) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }

    @Composable
    private fun ShortcutsSection(settings: Settings, actions: PanelActions) {
        SectionTitle("Atalhos")
        if (settings.shortcuts.isEmpty()) {
            Text(
                "Nenhum atalho ainda — adicione em Configurações → Meus atalhos.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            val rows = settings.shortcuts.chunked(4)
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        row.forEach { item ->
                            ShortcutButton(item, actions)
                        }
                        // preenche o resto da linha
                        repeat(4 - row.size) {
                            Spacer(Modifier.size(52.dp))
                        }
                    }
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    @Composable
    private fun ShortcutButton(item: ShortcutItem, actions: PanelActions) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(
                modifier = Modifier
                    .size(52.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { actions.onShortcut(item) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 2.dp
            ) {
                Box(contentAlignment = Alignment.Center) {
                    AppIcon(item, 52.dp)
                }
            }
            Spacer(Modifier.height(5.dp))
            Text(
                item.label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }

    @Composable
    private fun AppIcon(item: ShortcutItem, size: Dp) {
        val context = LocalContext.current
        val px = with(LocalDensity.current) { (size.value * density).toInt() }
        val bitmap = remember(item.packageName) {
            if (item.action == ShortcutAction.APP && item.packageName != null) {
                runCatching {
                    val drawable: Drawable =
                        context.packageManager.getApplicationIcon(item.packageName)
                    drawable.toBitmap(px, px).asImageBitmap()
                }.getOrNull()
            } else null
        }
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = item.label,
                modifier = Modifier.size(size)
            )
        } else {
            Icon(
                iconFor(item.action),
                contentDescription = item.label,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(size).padding(12.dp)
            )
        }
    }

    @Composable
    private fun MediaSection(m: MediaState, actions: PanelActions) {
        SectionTitle("Música")
        if (!m.active) {
            Text(
                "Nenhuma música tocando",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (m.albumArtUri != null) {
                    AsyncImage(
                        model = Uri.parse(m.albumArtUri),
                        contentDescription = "Álbum",
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.MusicNote, contentDescription = null)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        m.title,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        m.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                    Spacer(Modifier.height(6.dp))
                    val fraction = if (m.durationMs > 0) {
                        (m.positionMs.toFloat() / m.durationMs).coerceIn(0f, 1f)
                    } else 0f
                    LinearProgressIndicator(
                        progress = { fraction },
                        modifier = Modifier.fillMaxWidth().height(4.dp)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = actions.onMediaPrev) {
                    Icon(Icons.Filled.SkipPrevious, contentDescription = "Anterior")
                }
                Spacer(Modifier.width(12.dp))
                FilledIconButton(onClick = actions.onMediaPlayPause) {
                    Icon(
                        if (m.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (m.playing) "Pausar" else "Tocar"
                    )
                }
                Spacer(Modifier.width(12.dp))
                IconButton(onClick = actions.onMediaNext) {
                    Icon(Icons.Filled.SkipNext, contentDescription = "Próxima")
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    @Composable
    private fun TimerSection(tm: TimerState, actions: PanelActions) {
        SectionTitle("Temporizador")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = tm.mode == TimerMode.STOPWATCH,
                onClick = { actions.onTimerMode(TimerMode.STOPWATCH, 0) },
                label = { Text("Cronômetro") }
            )
            FilterChip(
                selected = tm.mode == TimerMode.TIMER,
                onClick = { actions.onTimerMode(TimerMode.TIMER, tm.targetMs.toInt() / 1000) },
                label = { Text("Temporizador") }
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = TimerController.format(tm.elapsedMs),
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            letterSpacing = 2.sp
        )
        if (tm.mode == TimerMode.TIMER) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(1, 5, 10, 15, 25).forEach { min ->
                    TextButton(onClick = { actions.onTimerMode(TimerMode.TIMER, min * 60) }) {
                        Text("$min min")
                    }
                }
            }
        }
        if (tm.finished) {
            Spacer(Modifier.height(4.dp))
            Text(
                "⏰ Tempo esgotado!",
                color = MaterialTheme.colorScheme.tertiary,
                fontWeight = FontWeight.SemiBold
            )
        }
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Button(onClick = actions.onTimerStartStop) {
                Icon(
                    if (tm.running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (tm.running) "Pausar" else "Iniciar")
            }
            Spacer(Modifier.width(8.dp))
            TextButton(onClick = actions.onTimerReset) {
                Icon(Icons.Filled.RestartAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Zerar")
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    @Composable
    private fun BrightnessSection(t: ToggleState, actions: PanelActions) {
        SectionTitle("Brilho")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Brightness6, contentDescription = null, modifier = Modifier.size(20.dp))
            Slider(
                value = t.brightness / 255f,
                onValueChange = { actions.onBrightness((it * 255).roundToInt()) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    @Composable
    private fun VolumeSection(t: ToggleState, actions: PanelActions) {
        SectionTitle("Volume")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VolumeUp, contentDescription = null, modifier = Modifier.size(20.dp))
            Slider(
                value = if (t.volumeMax > 0) t.volume / t.volumeMax.toFloat() else 0f,
                onValueChange = { actions.onVolume((it * t.volumeMax).roundToInt()) },
                modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
            )
        }
        Spacer(Modifier.height(16.dp))
    }

    @Composable
    private fun PremiumHint() {
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.Star,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "Widget Premium — desbloqueie em Configurações.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
        Spacer(Modifier.height(16.dp))
    }

    @Composable
    private fun SectionTitle(text: String) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.height(8.dp))
    }

    // ----------------------------------------------------------- Helpers

    fun iconFor(action: ShortcutAction): ImageVector = when (action) {
        ShortcutAction.FLASHLIGHT -> Icons.Filled.FlashlightOn
        ShortcutAction.SCREENSHOT -> Icons.Filled.Screenshot
        ShortcutAction.CAMERA -> Icons.Filled.CameraAlt
        ShortcutAction.DND -> Icons.Filled.DoNotDisturb
        ShortcutAction.ROTATION -> Icons.Filled.ScreenRotation
        ShortcutAction.RINGER -> Icons.Filled.Notifications
        ShortcutAction.POWER -> Icons.Filled.PowerSettingsNew
        ShortcutAction.NOTIFICATIONS -> Icons.Filled.Notifications
        ShortcutAction.SETTINGS -> Icons.Filled.Settings
        ShortcutAction.EXPAND -> Icons.Filled.OpenInFull
        ShortcutAction.APP -> Icons.Filled.Apps
        ShortcutAction.NONE -> Icons.Filled.Block
    }

    private fun timeNow(seconds: Boolean = false): String {
        val fmt = if (seconds) "%02d:%02d:%02d" else "%02d:%02d"
        val cal = java.util.Calendar.getInstance()
        val h = cal.get(java.util.Calendar.HOUR_OF_DAY)
        val m = cal.get(java.util.Calendar.MINUTE)
        val s = cal.get(java.util.Calendar.SECOND)
        return String.format(Locale.US, fmt, h, m, if (seconds) s else 0)
    }

}
