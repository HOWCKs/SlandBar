package com.slandbar.app.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

/** Reinicia a barra flutuante após o aparelho ser ligado (se habilitado). */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        // O próprio serviço verifica a preferência "autoStart" e se encerra se estiver desligada.
        OverlayManager.start(context)
    }
}
