package io.github.magisk317.mipush.app.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.os.Bundle

class RecentEventsWidgetProvider : AppWidgetProvider() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == MiPushWidgetActions.ACTION_REFRESH_RECENT_EVENTS ||
            intent.action == AppWidgetManager.ACTION_APPWIDGET_UPDATE
        ) {
            val pendingResult = goAsync()
            MiPushWidgetRunner.launch {
                runCatching { RecentEventsWidgetRenderer.updateAll(context) }
                pendingResult.finish()
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        MiPushWidgetRunner.launch {
            RecentEventsWidgetRenderer.update(context, appWidgetManager, appWidgetIds)
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle,
    ) {
        MiPushWidgetRunner.launch {
            RecentEventsWidgetRenderer.update(context, appWidgetManager, intArrayOf(appWidgetId))
        }
    }
}
