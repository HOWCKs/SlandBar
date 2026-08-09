package com.slandbar.app

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import com.slandbar.app.core.OverlayManager
import com.slandbar.app.overlay.SlandOverlayService

class SlandApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createOverlayChannel()
        // Se o usuário já ativou a barra, ela volta automaticamente ao abrir o app.
        // (try/catch: em Android 12+ iniciar FGS em background pode lançar exceção)
        runCatching { OverlayManager.start(this) }
    }

    private fun createOverlayChannel() {
        val channel = NotificationChannel(
            SlandOverlayService.CHANNEL_ID,
            getString(R.string.overlay_channel_name),
            NotificationManager.IMPORTANCE_MIN
        ).apply { description = getString(R.string.overlay_channel_desc) }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }
}
