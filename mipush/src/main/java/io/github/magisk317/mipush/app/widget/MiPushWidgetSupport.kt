@file:Suppress("MagicNumber")
package io.github.magisk317.mipush.app.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.text.TextPaint
import android.text.TextUtils
import android.os.Build
import android.os.SystemClock
import android.util.TypedValue
import android.widget.RemoteViews
import io.github.magisk317.mipush.app.R
import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.manager.ManagerEventType
import io.github.magisk317.mipush.feature.main.MainActivity
import io.github.magisk317.mipush.feature.navigation.AppDestinations
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.di.ManagerDependencies
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.reflect.KClass
import kotlin.math.max
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    fun settingsManager(): SettingsManager = get()

    fun eventGateway(): ManagerEventGateway = get()

    private inline fun <reified T : Any> get(): T = ManagerDependencies.get()
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

    fun refresh(context: Context, providerClass: KClass<*>, action: String, requestCode: Int): PendingIntent {
        val intent = Intent(context, providerClass.java).setAction(action)
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
        views.setOnClickPendingIntent(R.id.widget_root, MiPushWidgetIntents.openConnectionStatus(context))
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            MiPushWidgetIntents.refresh(
                context,
                ConnectionStatusWidgetProvider::class,
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
            // Android 17's launcher host treats a RemoteViews Chronometer base as wall-clock
            // milliseconds, while Android 16 uses the documented elapsedRealtime base. Passing
            // the converted elapsed base on API 37 makes the host display the Unix epoch as the
            // connection duration (the observed ~500k-hour value).
            val chronometerBase = if (Build.VERSION.SDK_INT >= 37) {
                snapshot.connectedAtMs
            } else {
                val elapsedSinceBoot = System.currentTimeMillis() - SystemClock.elapsedRealtime()
                snapshot.connectedAtMs - elapsedSinceBoot
            }
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
            context.getString(
                R.string.widget_connection_message_counts,
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
            val size = MiPushWidgetCanvas.widgetSizePx(
                appContext,
                manager,
                widgetId,
                fallbackWidthDp = 320,
                fallbackHeightDp = 210,
            )
            val displayCount = MiPushWidgetCanvas.recentEventsRowCapacity(appContext, size.heightPx)
            val views = buildViews(appContext, events.take(displayCount), size)
            manager.updateAppWidget(widgetId, views)
        }
    }

    private fun buildViews(
        context: Context,
        events: List<ManagerEvent>,
        size: MiPushWidgetCanvas.WidgetSize,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.mipush_widget_recent_events)
        views.setImageViewBitmap(
            R.id.widget_image,
            MiPushWidgetCanvas.drawRecentEvents(context, size.widthPx, size.heightPx, events),
        )
        views.setOnClickPendingIntent(R.id.widget_root, MiPushWidgetIntents.openRecentEvents(context))
        views.setOnClickPendingIntent(
            R.id.widget_refresh,
            MiPushWidgetIntents.refresh(
                context,
                RecentEventsWidgetProvider::class,
                MiPushWidgetActions.ACTION_REFRESH_RECENT_EVENTS,
                2002,
            ),
        )
        return views
    }

    private fun ManagerEvent.isRegistrationInfo(): Boolean {
        return type == ManagerEventType.REGISTRATION ||
            type == ManagerEventType.UN_REGISTRATION ||
            type == ManagerEventType.REGISTRATION_RESULT
    }
}

private fun ManagerEvent.displayTitle(context: Context): String {
    val appLabel = appName?.takeIf { it.isNotBlank() } ?: packageName
    return if (title.isBlank()) appLabel else context.getString(R.string.widget_event_title_format, appLabel, title)
}

private fun ManagerEvent.displayMeta(): String {
    return listOf(
        formatEventTime(receiveDateMs),
        channel.takeIf { it.isNotBlank() },
    ).filterNotNull().joinToString("  ")
}

private object MiPushWidgetCanvas {
    private const val COLOR_CARD = 0xFF1C1922.toInt()
    private const val COLOR_CARD_STROKE = 0xFF322D39.toInt()
    private const val COLOR_DIVIDER = 0xFF34303B.toInt()
    private const val COLOR_ICON_BACKGROUND = 0xFF28232D.toInt()
    private const val COLOR_TEXT_PRIMARY = 0xFFF0EBF6.toInt()
    private const val COLOR_TEXT_SECONDARY = 0xFFB8B0C2.toInt()
    private const val COLOR_TEXT_TERTIARY = 0xFF8D8498.toInt()

    data class WidgetSize(val widthPx: Int, val heightPx: Int)

    fun widgetSizePx(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        fallbackWidthDp: Int,
        fallbackHeightDp: Int,
    ): WidgetSize {
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
        val density = context.resources.displayMetrics.density
        return WidgetSize(
            widthPx = (widthDp * density).toInt().coerceAtLeast(240),
            heightPx = (heightDp * density).toInt().coerceAtLeast(160),
        )
    }

    fun widgetMinHeightDp(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        fallbackHeightDp: Int,
    ): Float {
        val options = manager.getAppWidgetOptions(widgetId)
        val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0)
            .takeIf { it > 0 }
            ?: fallbackHeightDp
        return max(fallbackHeightDp, heightDp).toFloat()
    }

    fun recentEventsRowCapacity(context: Context, heightPx: Int): Int {
        val heightDp = heightDp(context, heightPx)
        return ((heightDp - 130f) / 58f).toInt().coerceIn(3, 7)
    }

    fun heightDp(context: Context, heightPx: Int): Float {
        val density = context.resources.displayMetrics.density.coerceAtLeast(1f)
        return heightPx / density
    }

    fun drawRecentEvents(context: Context, widthPx: Int, heightPx: Int, events: List<ManagerEvent>): Bitmap {
        val bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val density = context.resources.displayMetrics.density
        val displayMetrics = context.resources.displayMetrics

        fun dp(value: Float) = value * density
        fun sp(value: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, value, displayMetrics)

        canvas.drawColor(Color.TRANSPARENT)
        val cardRect = RectF(1f, 1f, widthPx - 1f, heightPx - 1f)
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARD
            style = Paint.Style.FILL
        }
        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_CARD_STROKE
            style = Paint.Style.STROKE
            strokeWidth = dp(1f)
        }
        canvas.drawRoundRect(cardRect, dp(22f), dp(22f), fillPaint)
        canvas.drawRoundRect(cardRect, dp(22f), dp(22f), strokePaint)

        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = sp(20f)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.NORMAL)
        }
        val actionPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_SECONDARY
            textSize = sp(22f)
            textAlign = Paint.Align.RIGHT
        }
        val rowTitlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_PRIMARY
            textSize = sp(14.5f)
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val metaPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_TERTIARY
            textSize = sp(12.5f)
        }

        val left = dp(20f)
        val right = widthPx - dp(20f)
        val headerBaseline = dp(39f)
        canvas.drawText(context.getString(R.string.widget_recent_events_title), left, headerBaseline, titlePaint)
        canvas.drawText("↻", right, headerBaseline + dp(1f), actionPaint)
        canvas.drawLine(left, dp(60f), right, dp(60f), Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_DIVIDER
            strokeWidth = dp(1f)
        })

        if (events.isEmpty()) {
            val emptyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = COLOR_TEXT_SECONDARY
                textSize = sp(15f)
                textAlign = Paint.Align.CENTER
            }
            canvas.drawText(
                context.getString(R.string.widget_recent_events_empty),
                widthPx / 2f,
                heightPx / 2f + dp(5f),
                emptyPaint,
            )
        } else {
            val top = dp(92f)
            val bottomReserve = dp(44f)
            val rowSlots = recentEventsRowCapacity(context, heightPx)
            val available = (heightPx - top - bottomReserve).coerceAtLeast(dp(80f))
            val rowHeight = (available / rowSlots).coerceAtLeast(dp(56f))
            val iconSize = dp(36f)
            val textLeft = left + iconSize + dp(12f)
            events.forEachIndexed { index, event ->
                val rowTop = top + rowHeight * index
                val title = event.displayTitle(context)
                val meta = listOf(
                    event.displayMeta(),
                    event.content.ifBlank { event.channel },
                ).filter { it.isNotBlank() }.joinToString(" · ")
                drawEventIcon(
                    context = context,
                    canvas = canvas,
                    event = event,
                    left = left,
                    top = rowTop - dp(12f),
                    size = iconSize,
                    radius = dp(9f),
                )
                drawEllipsized(canvas, title, textLeft, rowTop, rowTitlePaint, right - textLeft)
                drawEllipsized(canvas, meta, textLeft, rowTop + dp(25f), metaPaint, right - textLeft)
            }
        }

        val updatedPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_TERTIARY
            textSize = sp(12f)
            textAlign = Paint.Align.RIGHT
        }
        canvas.drawText(formatUpdatedAt(context), right, heightPx - dp(18f), updatedPaint)
        return bitmap
    }

    private fun drawEventIcon(
        context: Context,
        canvas: Canvas,
        event: ManagerEvent,
        left: Float,
        top: Float,
        size: Float,
        radius: Float,
    ) {
        val icon = runCatching {
            context.packageManager.getApplicationIcon(event.packageName).mutate()
        }.getOrNull()
        val iconRect = RectF(left, top, left + size, top + size)
        val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_ICON_BACKGROUND
            style = Paint.Style.FILL
        }
        canvas.drawRoundRect(iconRect, radius, radius, backgroundPaint)
        if (icon == null) {
            drawIconPlaceholder(canvas, iconRect, event)
            return
        }

        val saveCount = canvas.save()
        val path = Path().apply {
            addRoundRect(iconRect, radius, radius, Path.Direction.CW)
        }
        canvas.clipPath(path)
        icon.setBounds(
            iconRect.left.toInt(),
            iconRect.top.toInt(),
            iconRect.right.toInt(),
            iconRect.bottom.toInt(),
        )
        icon.draw(canvas)
        canvas.restoreToCount(saveCount)
    }

    private fun drawIconPlaceholder(canvas: Canvas, iconRect: RectF, event: ManagerEvent) {
        val label = (event.appName ?: event.packageName)
            .trim()
            .takeIf { it.isNotEmpty() }
            ?.first()
            ?.uppercaseChar()
            ?.toString()
            ?: "-"
        val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_TEXT_SECONDARY
            textSize = iconRect.height() * 0.46f
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.create(android.graphics.Typeface.DEFAULT, android.graphics.Typeface.BOLD)
        }
        val centerY = iconRect.centerY() - (paint.descent() + paint.ascent()) / 2f
        canvas.drawText(label, iconRect.centerX(), centerY, paint)
    }

    private fun drawEllipsized(
        canvas: Canvas,
        text: String,
        x: Float,
        baseline: Float,
        paint: TextPaint,
        maxWidth: Float,
    ) {
        val safeText = text.ifBlank { "-" }
        val ellipsized = TextUtils.ellipsize(safeText, paint, maxWidth, TextUtils.TruncateAt.END)
        canvas.drawText(ellipsized.toString(), x, baseline, paint)
    }
}

private fun formatUpdatedAt(context: Context): String {
    return context.getString(R.string.widget_updated_at, formatEventTime(System.currentTimeMillis()))
}

private fun formatEventTime(timeMs: Long): String {
    if (timeMs <= 0L) return ""
    return SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timeMs))
}
