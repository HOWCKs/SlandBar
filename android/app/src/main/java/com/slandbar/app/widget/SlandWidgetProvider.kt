package com.slandbar.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import android.widget.Toast
import com.slandbar.app.MainActivity
import com.slandbar.app.R
import com.slandbar.app.core.ActionExecutor
import com.slandbar.app.core.model.ShortcutAction
import com.slandbar.app.core.model.ShortcutItem
import java.util.UUID

/**
 * Widget da tela inicial: 4 atalhos rápidos (lanterna, câmera, captura,
 * temporizador) sem precisar abrir o app.
 */
class SlandWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        ids.forEach { id -> update(context, manager, id) }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        val action = intent.action ?: return
        if (!action.startsWith(ACTION_PREFIX)) return

        val executor = ActionExecutor(context.applicationContext)
        when (action) {
            ACTION_FLASHLIGHT -> executor.execute(ShortcutAction.FLASHLIGHT)
            ACTION_CAMERA -> executor.execute(ShortcutAction.CAMERA)
            ACTION_SCREENSHOT -> executor.execute(ShortcutAction.SCREENSHOT)
            ACTION_TIMER -> {
                context.startActivity(
                    Intent(context, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
        // Re-sincroniza o estado (ex.: lanterna ligada/desligada não muda o ícone,
        // mas mantém o widget responsivo).
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, SlandWidgetProvider::class.java))
        ids.forEach { id -> update(context, manager, id) }
    }

    private fun update(context: Context, manager: AppWidgetManager, id: Int) {
        val views = RemoteViews(context.packageName, R.layout.widget_layout)
        views.setOnClickPendingIntent(R.id.wbtn_flashlight, pending(context, id, ACTION_FLASHLIGHT))
        views.setOnClickPendingIntent(R.id.wbtn_camera, pending(context, id, ACTION_CAMERA))
        views.setOnClickPendingIntent(R.id.wbtn_screenshot, pending(context, id, ACTION_SCREENSHOT))
        views.setOnClickPendingIntent(R.id.wbtn_timer, pending(context, id, ACTION_TIMER))
        manager.updateAppWidget(id, views)
    }

    private fun pending(context: Context, id: Int, action: String): PendingIntent {
        val intent = Intent(context, SlandWidgetProvider::class.java)
            .setAction(action)
            .setData(android.net.Uri.parse("slandbar://widget/$id/$action"))
        return PendingIntent.getBroadcast(
            context,
            id * 10 + action.hashCode() % 10,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    companion object {
        private const val ACTION_PREFIX = "com.slandbar.app.widget."
        private const val ACTION_FLASHLIGHT = "${ACTION_PREFIX}FLASHLIGHT"
        private const val ACTION_CAMERA = "${ACTION_PREFIX}CAMERA"
        private const val ACTION_SCREENSHOT = "${ACTION_PREFIX}SCREENSHOT"
        private const val ACTION_TIMER = "${ACTION_PREFIX}TIMER"
    }
}
