package com.slandbar.app.core

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.MediaStore
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.slandbar.app.MainActivity
import com.slandbar.app.R
import com.slandbar.app.accessibility.SlandAccessibilityService
import com.slandbar.app.core.model.ShortcutAction
import com.slandbar.app.core.model.ShortcutItem

/**
 * Executa as ações reais dos atalhos: lançar apps, lanterna, captura de tela,
 * DND, rotação, modo de som, menu de energia, sombras de notificação…
 *
 * Ações que dependem de permissões ausentes encaminham o usuário para a tela
 * principal com um pedido de permissão — nada é "simulado".
 */
class ActionExecutor(
    private val context: Context,
    private val onExpand: () -> Unit = {},
    private val onSystemChanged: () -> Unit = {}
) {

    object Torch {
        @Volatile
        var on = false
    }

    fun execute(item: ShortcutItem) = execute(item.action, item.packageName)

    fun execute(action: ShortcutAction, packageName: String? = null) {
        when (action) {
            ShortcutAction.NONE -> Unit
            ShortcutAction.EXPAND -> onExpand()
            ShortcutAction.APP -> launchApp(packageName)
            ShortcutAction.FLASHLIGHT -> toggleTorch()
            ShortcutAction.SCREENSHOT -> takeScreenshot()
            ShortcutAction.CAMERA -> openCamera()
            ShortcutAction.DND -> toggleDnd()
            ShortcutAction.ROTATION -> toggleRotation()
            ShortcutAction.RINGER -> cycleRinger()
            ShortcutAction.POWER -> openPowerMenu()
            ShortcutAction.NOTIFICATIONS -> openNotifications()
            ShortcutAction.SETTINGS -> openAppSettings()
        }
    }

    // ---- Apps ----

    private fun launchApp(packageName: String?) {
        if (packageName.isNullOrBlank()) {
            toast(context.getString(R.string.toast_no_app))
            return
        }
        val intent = context.packageManager.getLaunchIntentForPackage(packageName)
        if (intent == null) {
            toast(context.getString(R.string.toast_no_app))
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { toast(context.getString(R.string.toast_no_app)) }
    }

    // ---- Lanterna ----

    private fun toggleTorch() {
        if (!hasPermission(Manifest.permission.CAMERA)) {
            requestPermissionInApp("camera")
            return
        }
        val manager = context.getSystemService(CameraManager::class.java) ?: return
        val id = manager.cameraIdList.firstOrNull { id ->
            runCatching { manager.getCameraCharacteristics(id).get(
                android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE
            ) == true }.getOrDefault(false)
        } ?: manager.cameraIdList.firstOrNull()
        if (id == null) {
            toast(context.getString(R.string.widget_flashlight) + ": não disponível")
            return
        }
        val target = !Torch.on
        runCatching { manager.setTorchMode(id, target) }
            .onSuccess {
                Torch.on = target
                onSystemChanged()
            }
            .onFailure {
                Torch.on = false
                toast(context.getString(R.string.widget_flashlight) + ": falhou")
            }
    }

    // ---- Captura de tela (via AccessibilityService, API 30+) ----

    private fun takeScreenshot() {
        if (!SlandAccessibilityService.enabled) {
            requestPermissionInApp("accessibility")
            return
        }
        SlandAccessibilityService.requestScreenshot { success ->
            if (success) toast("📸 " + context.getString(R.string.widget_screenshot))
            else toast(context.getString(R.string.widget_screenshot) + ": falhou")
        }
    }

    // ---- Câmera ----

    private fun openCamera() {
        val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
        runCatching {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }

    // ---- Não perturbe ----

    private fun toggleDnd() {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (!nm.isNotificationPolicyAccessGranted) {
            openSystemSettings(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
            return
        }
        val next = if (nm.currentInterruptionFilter == NotificationManager.INTERRUPTION_FILTER_ALL) {
            NotificationManager.INTERRUPTION_FILTER_PRIORITY
        } else {
            NotificationManager.INTERRUPTION_FILTER_ALL
        }
        nm.setInterruptionFilter(next)
        onSystemChanged()
    }

    // ---- Rotação ----

    private fun toggleRotation() {
        if (!Settings.System.canWrite(context)) {
            openSystemSettings(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            return
        }
        val auto = Settings.System.getInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            1
        ) == 1
        Settings.System.putInt(
            context.contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            if (auto) 0 else 1
        )
        onSystemChanged()
    }

    // ---- Modo de som (normal → vibrar → silencioso) ----

    private fun cycleRinger() {
        val am = context.getSystemService(AudioManager::class.java)
        val next = when (am.ringerMode) {
            AudioManager.RINGER_MODE_NORMAL -> AudioManager.RINGER_MODE_VIBRATE
            AudioManager.RINGER_MODE_VIBRATE -> AudioManager.RINGER_MODE_SILENT
            else -> AudioManager.RINGER_MODE_NORMAL
        }
        am.ringerMode = next
        onSystemChanged()
    }

    // ---- Menu de energia / sombra de notificações (via acessibilidade) ----

    private fun openPowerMenu() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && SlandAccessibilityService.enabled) {
            SlandAccessibilityService.performGlobalActionCompat(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_POWER_DIALOG
            )
        } else {
            requestPermissionInApp("accessibility")
        }
    }

    private fun openNotifications() {
        if (SlandAccessibilityService.enabled) {
            SlandAccessibilityService.performGlobalActionCompat(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS
            )
        } else {
            requestPermissionInApp("accessibility")
        }
    }

    // ---- Helpers ----

    private fun openAppSettings() {
        context.startActivity(
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    private fun requestPermissionInApp(which: String) {
        context.startActivity(
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_REQUEST_PERMISSION, which)
        )
    }

    private fun openSystemSettings(action: String) {
        runCatching {
            context.startActivity(
                Intent(action, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }

    /** Reativa a tela se estiver desligada (usado por ações que abrem UI). */
    @Suppress("unused")
    private fun wakeUp() {
        val pm = context.getSystemService(PowerManager::class.java) ?: return
        if (!pm.isInteractive) {
            runCatching {
                pm.newWakeLock(PowerManager.FULL_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP, "slandbar:wake")
                    .apply { acquire(1500) }
            }
        }
    }
}
