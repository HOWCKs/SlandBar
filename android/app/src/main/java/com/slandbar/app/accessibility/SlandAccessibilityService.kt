package com.slandbar.app.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import com.slandbar.app.overlay.SlandOverlayService
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Serviço de acessibilidade da SlandBar.
 *
 * Usado exclusivamente para:
 *  1. Captura de tela (takeScreenshot, Android 11+) a partir dos atalhos;
 *  2. Menu de energia e sombra de notificações;
 *  3. Recolher a barra automaticamente quando o teclado estiver aberto.
 *
 * Nenhum dado pessoal é coletado, armazenado ou compartilhado
 * (texto obrigatório pela política do Google Play).
 */
class SlandAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "SlandAccessibility"

        @Volatile
        var enabled = false
            private set

        @Volatile
        private var instance: SlandAccessibilityService? = null

        @Volatile
        private var pendingScreenshot: ((Boolean) -> Unit)? = null

        /** Ligação com o serviço de overlay para avisar sobre o teclado. */
        @Volatile
        var onKeyboardVisibilityChanged: ((Boolean) -> Unit)? = null

        /** Pede uma captura de tela; o callback recebe true em caso de sucesso. */
        fun requestScreenshot(callback: (Boolean) -> Unit) {
            if (!enabled) {
                callback(false)
                return
            }
            pendingScreenshot = callback
        }

        /** Executa uma ação global (menu de energia, notificações…) com segurança de thread. */
        fun performGlobalActionCompat(action: Int) {
            val service = instance ?: return
            Handler(Looper.getMainLooper()).post {
                service.performGlobalAction(action)
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        enabled = true
        instance = this
        Log.i(TAG, "Serviço de acessibilidade conectado")
    }

    override fun onDestroy() {
        enabled = false
        if (instance === this) instance = null
        super.onDestroy()
    }

    override fun onInterrupt() = Unit

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return

        // Teclado aberto → barra se recolhe para não atrapalhar a digitação.
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val cls = event.className?.toString().orEmpty()
            val pkg = event.packageName?.toString().orEmpty()
            if (cls.contains("SoftInput") || pkg.contains("inputmethod")) {
                SlandOverlayService.onKeyboardVisibilityChanged(true)
            }
        }

        // Dispara uma captura de tela pendente.
        val cb = pendingScreenshot ?: return
        pendingScreenshot = null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            cb(false)
            return
        }
        takeScreenshot(
            Display.DEFAULT_DISPLAY,
            { it.run() },
            object : TakeScreenshotCallback {
                override fun onSuccess(screenshot: ScreenshotResult) {
                    saveScreenshot(screenshot)
                    cb(true)
                }

                override fun onFailure(errorCode: Int) {
                    Log.e(TAG, "Falha na captura: $errorCode")
                    cb(false)
                }
            }
        )
    }

    private fun saveScreenshot(screenshot: ScreenshotResult) {
        val bitmap = Bitmap.wrapHardwareBuffer(screenshot.hardwareBuffer, screenshot.colorSpace)
        if (bitmap == null) {
            toast("Captura falhou")
            return
        }
        try {
            val resolver = contentResolver
            val now = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, "SlandBar_$now.png")
                put(MediaStore.Images.Media.MIME_TYPE, "image/png")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(
                        MediaStore.Images.Media.RELATIVE_PATH,
                        Environment.DIRECTORY_PICTURES + "/SlandBar"
                    )
                    put(MediaStore.Images.Media.IS_PENDING, 1)
                }
            }
            val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: run {
                    toast("Captura falhou")
                    return
                }
            val out: OutputStream? = resolver.openOutputStream(uri)
            if (out != null) {
                out.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                values.clear()
                values.put(MediaStore.Images.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
            }
            toast("📸 Captura salva na galeria")
        } catch (e: Exception) {
            Log.e(TAG, "Erro ao salvar captura", e)
            toast("Captura falhou")
        } finally {
            bitmap.recycle()
        }
    }

    private fun toast(msg: String) {
        Handler(Looper.getMainLooper()).post {
            Toast.makeText(applicationContext, msg, Toast.LENGTH_SHORT).show()
        }
    }
}
