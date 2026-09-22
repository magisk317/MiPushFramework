@file:Suppress("MagicNumber")
package io.github.magisk317.mipush.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Build
import android.os.SystemClock
import android.view.View
import android.widget.RemoteViews
import io.github.magisk317.mipush.app.R
import io.github.magisk317.mipush.manager.application.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.ManagerEventGateway
import io.github.magisk317.mipush.manager.application.ManagerEventType
import io.github.magisk317.mipush.feature.main.MainActivity
import io.github.magisk317.mipush.feature.navigation.AppDestinations
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.di.ManagerDependencies
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** MIUI/HyperOS proprietary refresh broadcast that drives exposure-based updates. */
internal const val MIUI_APPWIDGET_UPDATE = "miui.appwidget.action.APPWIDGET_UPDATE"

internal object MiPushWidgetActions {
    const val ACTION_REFRESH_CONNECTION = "io.github.magisk317.mipush.app.widget.REFRESH_CONNECTION"
    const val ACTION_REFRESH_RECENT_EVENTS = "io.github.magisk317.mipush.app.widget.REFRESH_RECENT_EVENTS"
}

internal object MiPushWidgetRunner {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun launch(block: suspend () -> Unit) {
        scope.launch { block() }
    }
}

internal object MiPushWidgetDependencies {
    fun settingsManager(): SettingsManager = ManagerDependencies.get()

    fun eventGateway(): ManagerEventGateway = ManagerDependencies.get()
}

internal object MiPushWidgetIntents {
    fun openConnectionStatus(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_START_ROUTE, AppDestinations.ConnectionStatus.ROUTE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            1001,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun openRecentEvents(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .putExtra(MainActivity.EXTRA_START_ROUTE, AppDestinations.EventsList.ROUTE)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        return PendingIntent.getActivity(
            context,
            1002,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun refresh(context: Context, providerClass: Class<*>, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, providerClass).setAction(action)
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

internal object ConnectionStatusWidgetRenderer {
    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext ?: context
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, ConnectionStatusWidgetProvider::class.java)
        val widgetIds = manager.getAppWidgetIds(component)
        if (widgetIds.isNotEmpty()) {
            update(appContext, manager, widgetIds)
        }
    }

    suspend fun update(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val appContext = context.applicationContext ?: context
        val snapshot = runCatching {
            MiPushWidgetDependencies.settingsManager().getConnectionSnapshot()
        }.getOrNull()
        widgetIds.forEach { widgetId ->
            val views = buildViews(appContext, snapshot, compact = true)
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun buildViews(
        context: Context,
        snapshot: ManagerConnectionSnapshot?,
        compact: Boolean,
    ): RemoteViews {
        val views = RemoteViews(
            context.packageName,
            if (compact) {
                R.layout.mipush_widget_connection_status_compact
            } else {
                R.layout.mipush_widget_connection_status
            },
        )
        views.setOnClickPendingIntent(android.R.id.background, MiPushWidgetIntents.openConnectionStatus(context))
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            MiPushWidgetIntents.refresh(
                context,
                ConnectionStatusWidgetProvider::class.java,
                MiPushWidgetActions.ACTION_REFRESH_CONNECTION,
                2001,
            ),
        )

        if (snapshot == null) {
            views.setImageViewResource(R.id.widget_status_dot, R.drawable.mipush_widget_dot_warning)
            views.setTextViewText(R.id.widget_status_value, context.getString(R.string.widget_status_unavailable))
            views.setTextViewText(R.id.widget_status_detail, context.getString(R.string.widget_status_tap_refresh))
            views.setTextViewText(R.id.widget_duration_label, context.getString(R.string.widget_connection_duration))
            views.setTextViewText(R.id.widget_duration_value, context.getString(R.string.connection_status_not_available))
            views.setTextViewText(R.id.widget_server_value, context.getString(R.string.connection_status_not_available))
            views.setTextViewText(R.id.widget_messages_value, context.getString(R.string.connection_status_not_available))
            views.setTextViewText(R.id.widget_updated_at, formatUpdatedAt(context))
            return views
        }

        val isConnected = snapshot.connectionState.equals("Connected", ignoreCase = true)
        views.setImageViewResource(
            R.id.widget_status_dot,
            if (isConnected) R.drawable.mipush_widget_dot_connected else R.drawable.mipush_widget_dot_disconnected,
        )
        views.setTextViewText(R.id.widget_status_value, snapshot.connectionState)
        views.setTextViewText(
            R.id.widget_status_detail,
            context.getString(R.string.widget_connection_session, snapshot.connectionSessionCount),
        )
        views.setTextViewText(R.id.widget_duration_label, context.getString(R.string.widget_connection_duration))
        if (isConnected && snapshot.connectedAtMs > 0L) {
            // Chronometer 的 base 永远运行在 SystemClock.elapsedRealtime() 时基上，
            // wall-clock 的 connectedAtMs 必须换算，否则显示为巨大的负值
            val elapsedSinceBoot = System.currentTimeMillis() - SystemClock.elapsedRealtime()
            val chronometerBase = snapshot.connectedAtMs - elapsedSinceBoot
            views.setChronometer(R.id.widget_duration_value, chronometerBase, "%s", true)
        } else {
            views.setChronometer(R.id.widget_duration_value, SystemClock.elapsedRealtime(), "%s", false)
            views.setTextViewText(R.id.widget_duration_value, context.getString(R.string.connection_status_not_available))
        }
        views.setTextViewText(
            R.id.widget_server_value,
            listOfNotNull(snapshot.serverHost, snapshot.serverIp?.takeIf { it.isNotBlank() })
                .joinToString(" / ")
                .ifBlank { context.getString(R.string.connection_status_not_available) },
        )
        views.setTextViewText(
            R.id.widget_messages_value,
            context.resources.getQuantityString(
                R.plurals.widget_connection_message_counts,
                snapshot.downstreamMessageCount.coerceIn(0L, Int.MAX_VALUE.toLong()).toInt(),
                snapshot.downstreamMessageCount,
                snapshot.deliveredToAppCount,
            ),
        )
        views.setTextViewText(R.id.widget_updated_at, formatUpdatedAt(context))
        return views
    }
}

internal object RecentEventsWidgetRenderer {
    private const val QUERY_COUNT = 48
    private const val MAX_ROWS = 7
    private const val MIN_ROWS = 3

    suspend fun updateAll(context: Context) {
        val appContext = context.applicationContext ?: context
        val manager = AppWidgetManager.getInstance(appContext)
        val component = ComponentName(appContext, RecentEventsWidgetProvider::class.java)
        val widgetIds = manager.getAppWidgetIds(component)
        if (widgetIds.isNotEmpty()) {
            update(appContext, manager, widgetIds)
        }
    }

    suspend fun update(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val appContext = context.applicationContext ?: context
        val events = runCatching {
            MiPushWidgetDependencies.eventGateway()
                .getEventsById(lastId = null, size = QUERY_COUNT, packageName = "", query = "")
                .filterNot { it.isRegistrationInfo() }
        }.getOrElse { emptyList() }
        widgetIds.forEach { widgetId ->
            val sizeDp = MiPushWidgetMetrics.widgetSizeDp(
                appContext,
                manager,
                widgetId,
                fallbackWidthDp = 250,
                fallbackHeightDp = 360,
            )
            val capacity = MiPushWidgetMetrics.recentEventsRowCapacity(sizeDp.heightDp)
                .coerceIn(MIN_ROWS, MAX_ROWS)
            val views = buildViews(appContext, events.take(capacity))
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun buildViews(
        context: Context,
        events: List<ManagerEvent>,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.mipush_widget_recent_events)
        views.setOnClickPendingIntent(android.R.id.background, MiPushWidgetIntents.openRecentEvents(context))
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            MiPushWidgetIntents.refresh(
                context,
                RecentEventsWidgetProvider::class.java,
                MiPushWidgetActions.ACTION_REFRESH_RECENT_EVENTS,
                2002,
            ),
        )

        if (events.isEmpty()) {
            views.setViewVisibility(R.id.widget_empty, View.VISIBLE)
        } else {
            views.setViewVisibility(R.id.widget_empty, View.GONE)
            val shown = events.take(recentRowIds.size)
            shown.forEachIndexed { index, event ->
                views.setViewVisibility(recentRowIds[index], View.VISIBLE)
                views.setTextViewText(recentTitleIds[index], event.displayTitle(context))
                views.setTextViewText(recentMetaIds[index], event.displayMetaLine())
                loadAppIcon(context, event.packageName)?.let {
                    views.setImageViewBitmap(recentIconIds[index], it)
                }
            }
            for (index in shown.size until recentRowIds.size) {
                views.setViewVisibility(recentRowIds[index], View.GONE)
            }
        }
        views.setTextViewText(R.id.widget_updated_at, formatUpdatedAt(context))
        return views
    }

    private val recentRowIds = intArrayOf(
        R.id.widget_row_0, R.id.widget_row_1, R.id.widget_row_2, R.id.widget_row_3,
        R.id.widget_row_4, R.id.widget_row_5, R.id.widget_row_6,
    )
    private val recentTitleIds = intArrayOf(
        R.id.row_title_0, R.id.row_title_1, R.id.row_title_2, R.id.row_title_3,
        R.id.row_title_4, R.id.row_title_5, R.id.row_title_6,
    )
    private val recentMetaIds = intArrayOf(
        R.id.row_meta_0, R.id.row_meta_1, R.id.row_meta_2, R.id.row_meta_3,
        R.id.row_meta_4, R.id.row_meta_5, R.id.row_meta_6,
    )
    private val recentIconIds = intArrayOf(
        R.id.row_icon_0, R.id.row_icon_1, R.id.row_icon_2, R.id.row_icon_3,
        R.id.row_icon_4, R.id.row_icon_5, R.id.row_icon_6,
    )

    private fun loadAppIcon(context: Context, packageName: String): Bitmap? {
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            val density = context.resources.displayMetrics.density
            val size = (36 * density).toInt().coerceAtLeast(1)
            val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            drawable.setBounds(0, 0, size, size)
            drawable.draw(canvas)
            bitmap
        }.getOrNull()
    }

    private fun ManagerEvent.isRegistrationInfo(): Boolean {
        return type == ManagerEventType.REGISTRATION ||
            type == ManagerEventType.UN_REGISTRATION ||
            type == ManagerEventType.REGISTRATION_RESULT
    }
}

private object MiPushWidgetMetrics {
    data class WidgetSizeDp(val widthDp: Int, val heightDp: Int)

    fun widgetSizeDp(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        fallbackWidthDp: Int,
        fallbackHeightDp: Int,
    ): WidgetSizeDp {
        val options = manager.getAppWidgetOptions(widgetId)
        val widthDp = max(
            fallbackWidthDp,
            max(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 0),
            ),
        )
        val heightDp = max(
            fallbackHeightDp,
            max(
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 0),
            ),
        )
        return WidgetSizeDp(widthDp = widthDp, heightDp = heightDp)
    }

    fun recentEventsRowCapacity(heightDp: Int): Int {
        // header（标题+更新时间+padding）约 80dp，每行（icon 36dp + 上下 padding）约 48dp
        return ((heightDp - 80f) / 48f).toInt()
    }
}

private fun ManagerEvent.displayTitle(context: Context): String {
    val appLabel = appName?.takeIf { it.isNotBlank() } ?: packageName
    return if (title.isBlank()) appLabel else context.getString(R.string.widget_event_title_format, appLabel, title)
}

private fun ManagerEvent.displayMetaLine(): String {
    val timeChannel = listOf(formatEventTime(receiveDateMs), channel.takeIf { it.isNotBlank() })
        .filterNotNull()
        .joinToString("  ")
    return listOf(timeChannel, content.ifBlank { null })
        .filterNotNull()
        .joinToString(" · ")
}

private fun formatUpdatedAt(context: Context): String {
    return context.getString(R.string.widget_updated_at, formatEventTime(System.currentTimeMillis()))
}

private fun formatEventTime(timeMs: Long): String {
    if (timeMs <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
}
