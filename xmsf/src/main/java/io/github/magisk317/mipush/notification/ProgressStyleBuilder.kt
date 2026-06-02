package io.github.magisk317.mipush.notification

import android.app.Notification
import android.content.Context
import android.os.Build
import android.text.SpannableString
import android.text.Spanned
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.aakira.napier.Napier

/**
 * Builds progress-style notifications for Live Updates.
 *
 * On Android 16+ (API 36+): uses Notification.ProgressStyle with promoted ongoing.
 * On older Android: falls back to simulated progress using standard progress bar or BigTextStyle.
 *
 * Framework-side implementation — apps do not need to adapt.
 */
object ProgressStyleBuilder {

    private const val TAG = "ProgressStyleBuilder"

    /**
     * Enhance a notification builder with progress/Live Update styling.
     *
     * @param context Application context
     * @param builder Base notification builder
     * @param metaInfo Push message metadata
     * @param detectionResult Result from LiveUpdateDetector
     * @return The enhanced builder (may be replaced with a platform-specific builder on Android 16+)
     */
    @JvmStatic
    fun applyProgressStyle(
        context: Context,
        builder: NotificationCompat.Builder,
        metaInfo: PushMetaInfo,
        detectionResult: LiveUpdateDetector.DetectionResult,
        semanticStyle: FocusSemanticTranslator.SemanticStyle =
            FocusSemanticTranslator.semanticStyleForCategory(detectionResult.category),
    ): NotificationCompat.Builder {
        if (!detectionResult.isProgress) {
            return builder
        }

        markLiveUpdate(builder, detectionResult, semanticStyle)
        return applyFallbackProgressStyle(builder, detectionResult)
    }

    @JvmStatic
    fun buildNotification(context: Context, builder: NotificationCompat.Builder): Notification {
        return applyNativeProgressStyleIfNeeded(context, builder.build())
    }

    @JvmStatic
    fun isLiveUpdate(builder: NotificationCompat.Builder): Boolean {
        return builder.extras.getBoolean(EXTRA_LIVE_UPDATE, false)
    }

    @JvmStatic
    fun applyNativeProgressStyleIfNeeded(context: Context, notification: Notification): Notification {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.BAKLAVA) {
            return notification
        }
        if (!notification.extras.getBoolean(EXTRA_LIVE_UPDATE, false)) {
            return notification
        }
        if (notification.hasCustomRemoteViews()) {
            Napier.d("Skip native ProgressStyle because notification uses custom RemoteViews", tag = TAG)
            return notification
        }
        return try {
            applyNativeProgressStyle(context, notification)
        } catch (e: Exception) {
            Napier.e("Failed to apply native ProgressStyle, keeping fallback notification", e, tag = TAG)
            notification
        }
    }

    @RequiresApi(Build.VERSION_CODES.BAKLAVA)
    private fun applyNativeProgressStyle(
        context: Context,
        notification: Notification
    ): Notification {
        val extras = notification.extras
        val platformBuilder = Notification.Builder.recoverBuilder(context, notification)
        val style = Notification.ProgressStyle().setStyledByProgress(true)
        val progress = extras.getInt(EXTRA_LIVE_UPDATE_PROGRESS, NO_PROGRESS)
        if (progress == NO_PROGRESS) {
            style.setProgressIndeterminate(true)
        } else {
            val safeProgress = progress.coerceIn(0, 100)
            style.setProgress(safeProgress)
            val segment = Notification.ProgressStyle.Segment(100)
            val point = Notification.ProgressStyle.Point(safeProgress)
            if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
                val semanticStyle = extras.getString(EXTRA_LIVE_UPDATE_SEMANTIC_STYLE)
                    ?.let { runCatching { FocusSemanticTranslator.SemanticStyle.valueOf(it) }.getOrNull() }
                    ?: FocusSemanticTranslator.SemanticStyle.INFO
                applySemanticStyle(segment, point, semanticStyle)
                annotateContentText(platformBuilder, notification, semanticStyle)
            }
            style.addProgressSegment(segment)
            style.addProgressPoint(point)
        }
        val shortText = extras.getString(EXTRA_LIVE_UPDATE_SHORT_TEXT)?.take(MAX_SHORT_CRITICAL_TEXT)
        if (!shortText.isNullOrBlank()) {
            platformBuilder.setShortCriticalText(shortText)
        }
        platformBuilder
            .setStyle(style)
            .setOngoing(true)
            .setAutoCancel(false)
        if (Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1) {
            requestPromotedOngoing(platformBuilder)
        }
        return platformBuilder
            .build()
    }

    @RequiresApi(Build.VERSION_CODES_FULL.BAKLAVA_1)
    private fun requestPromotedOngoing(builder: Notification.Builder) {
        builder.setRequestPromotedOngoing(true)
    }

    @RequiresApi(Build.VERSION_CODES_FULL.BAKLAVA_1)
    private fun applySemanticStyle(
        segment: Notification.ProgressStyle.Segment,
        point: Notification.ProgressStyle.Point,
        semanticStyle: FocusSemanticTranslator.SemanticStyle,
    ) {
        val platformStyle = FocusSemanticTranslator.toPlatformSemanticStyle(semanticStyle)
        segment.setSemanticStyle(platformStyle)
        point.setSemanticStyle(platformStyle)
    }

    @RequiresApi(Build.VERSION_CODES_FULL.BAKLAVA_1)
    private fun annotateContentText(
        builder: Notification.Builder,
        notification: Notification,
        semanticStyle: FocusSemanticTranslator.SemanticStyle,
    ) {
        val text = notification.extras.getCharSequence(Notification.EXTRA_TEXT)
            ?.takeIf { it.isNotBlank() }
            ?: return
        val annotated = SpannableString(text).apply {
            setSpan(
                Notification.createSemanticStyleAnnotation(
                    FocusSemanticTranslator.toPlatformSemanticStyle(semanticStyle)
                ),
                0,
                length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
            )
        }
        builder.setContentText(annotated)
    }

    private fun applyFallbackProgressStyle(
        builder: NotificationCompat.Builder,
        result: LiveUpdateDetector.DetectionResult
    ): NotificationCompat.Builder {
        Napier.d("Applying fallback progress style for Android ${Build.VERSION.SDK_INT}", tag = TAG)

        // Set as ongoing for progress notifications
        builder.setOngoing(true)
        builder.setAutoCancel(false)

        // Apply standard progress bar
        result.progressPercent?.let { percent ->
            builder.setProgress(100, percent, false)
        } ?: run {
            // Indeterminate progress if no percentage available
            builder.setProgress(0, 0, true)
        }

        // Use BigTextStyle to show full progress description when expanded
        val bigText = result.progressText
        if (!bigText.isNullOrBlank()) {
            val style = NotificationCompat.BigTextStyle()
            style.bigText(bigText)
            result.trackerLabel?.let { style.setSummaryText(it) }
            builder.setStyle(style)
        }

        // High priority for visibility
        builder.priority = NotificationCompat.PRIORITY_HIGH

        return builder
    }

    private fun markLiveUpdate(
        builder: NotificationCompat.Builder,
        result: LiveUpdateDetector.DetectionResult,
        semanticStyle: FocusSemanticTranslator.SemanticStyle,
    ) {
        val extras = builder.extras
        extras.putBoolean(EXTRA_LIVE_UPDATE, true)
        extras.putString(EXTRA_LIVE_UPDATE_CATEGORY, result.category.label)
        extras.putString(
            EXTRA_LIVE_UPDATE_SHORT_TEXT,
            result.trackerLabel ?: result.progressText?.take(MAX_SHORT_CRITICAL_TEXT)
        )
        extras.putInt(EXTRA_LIVE_UPDATE_PROGRESS, result.progressPercent ?: NO_PROGRESS)
        extras.putString(EXTRA_LIVE_UPDATE_SEMANTIC_STYLE, semanticStyle.name)
        builder.addExtras(extras)
    }

    @Suppress("DEPRECATION")
    private fun Notification.hasCustomRemoteViews(): Boolean {
        return contentView != null || bigContentView != null || headsUpContentView != null
    }

    internal const val EXTRA_LIVE_UPDATE = "xmsf.live_update"
    internal const val EXTRA_LIVE_UPDATE_CATEGORY = "xmsf.live_update.category"
    internal const val EXTRA_LIVE_UPDATE_SHORT_TEXT = "xmsf.live_update.short_text"
    internal const val EXTRA_LIVE_UPDATE_PROGRESS = "xmsf.live_update.progress"
    internal const val EXTRA_LIVE_UPDATE_SEMANTIC_STYLE = "xmsf.live_update.semantic_style"
    private const val NO_PROGRESS = -1
    private const val MAX_SHORT_CRITICAL_TEXT = 15
}
