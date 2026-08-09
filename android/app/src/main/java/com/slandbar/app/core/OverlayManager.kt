package com.slandbar.app.core

import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.slandbar.app.overlay.SlandOverlayService

/** Ponto único para ligar/desligar o serviço da barra flutuante. */
object OverlayManager {

    fun canDrawOverlays(context: Context): Boolean =
        Settings.canDrawOverlays(context)

    fun start(context: Context) {
        if (!canDrawOverlays(context)) return
        val intent = Intent(context, SlandOverlayService::class.java)
            .setAction(SlandOverlayService.ACTION_START)
        ContextCompat.startForegroundService(context, intent)
    }

    fun stop(context: Context) {
        context.stopService(Intent(context, SlandOverlayService::class.java))
    }

    fun isRunning(): Boolean = SlandOverlayService.isRunning
}
