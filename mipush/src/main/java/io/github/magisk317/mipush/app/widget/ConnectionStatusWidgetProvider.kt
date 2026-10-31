package io.github.magisk317.mipush.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.common.utils.logW

class ConnectionStatusWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MiPushWidgetActions.ACTION_REFRESH_CONNECTION ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE ||
            intent.action == MIUI_APPWIDGET_UPDATE
        ) {
            val pendingResult = goAsync()
            MiPushWidgetRunner.launch {
                runCatching { ConnectionStatusWidgetRenderer.updateAll(context) }
                    .onFailure { error ->
                        // The widget keeps its previous render; the broadcast receiver still has to
                        // finish, so this reports rather than retries.
                        this@ConnectionStatusWidgetProvider.logW("connection widget refresh failed: ${error.message}", error)
                    }
                pendingResult.finish()
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        MiPushWidgetRunner.launch {
            ConnectionStatusWidgetRenderer.update(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: android.os.Bundle,
    ) {
        MiPushWidgetRunner.launch {
            ConnectionStatusWidgetRenderer.update(context, appWidgetManager, intArrayOf(appWidgetId))
        }
    }
}
