package io.github.magisk317.mipush.notification

import android.content.Context
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.aakira.napier.Napier

/**
 * Detects progress-style (Live Update) notifications from push message content.
 *
 * This is a thin adapter that converts Android/protocol types to platform-independent
 * types and delegates to [LiveUpdateDetectorCore] for the actual detection logic.
 *
 * Categories detected:
 * - Delivery / food delivery (外卖配送)
 * - Ride-hailing / taxi (打车)
 * - Logistics / package tracking (物流)
 * - Download / installation (下载安装)
 * - Travel / itinerary (行程)
 */
object LiveUpdateDetector {

    private const val TAG = "LiveUpdateDetector"

    /**
     * Type alias for [LiveUpdateDetectorCore.ProgressCategory].
     * Preserves existing public API for callers referencing `LiveUpdateDetector.ProgressCategory`.
     */
    typealias ProgressCategory = LiveUpdateDetectorCore.ProgressCategory

    /**
     * Type alias for [LiveUpdateDetectorCore.DetectionResult].
     * Preserves existing public API for callers referencing `LiveUpdateDetector.DetectionResult`.
     */
    typealias DetectionResult = LiveUpdateDetectorCore.DetectionResult

    /**
     * Analyze a push message and determine if it represents a progress/Live Update notification.
     *
     * Converts [PushMetaInfo] fields to a platform-independent [LiveUpdateDetectorCore.DetectionInput]
     * and delegates to [LiveUpdateDetectorCore.detect].
     *
     * @param context Application context
     * @param metaInfo Push message metadata
     * @param packageName Source package name
     * @return DetectionResult with category, progress percentage, and labels
     */
    @JvmStatic
    fun detect(
        context: Context,
        metaInfo: PushMetaInfo?,
        packageName: String
    ): DetectionResult {
        if (metaInfo == null) return DetectionResult.NONE

        val input = LiveUpdateDetectorCore.DetectionInput(
            title = metaInfo.title ?: "",
            description = metaInfo.description ?: "",
            packageName = packageName
        )

        val result = LiveUpdateDetectorCore.detect(input)

        if (result.isProgress) {
            Napier.i(
                "Live Update detected pkg=$packageName category=${result.category} " +
                    "progress=${result.progressPercent} title=${input.title} description=${input.description}",
                tag = TAG
            )
        }

        return result
    }

    /**
     * Quick check: does this message look like it could be a Live Update?
     * Use this for early-return in hot paths.
     *
     * Delegates to [LiveUpdateDetectorCore.isPotentialLiveUpdate].
     */
    @JvmStatic
    fun isPotentialLiveUpdate(metaInfo: PushMetaInfo?): Boolean {
        if (metaInfo == null) return false
        return LiveUpdateDetectorCore.isPotentialLiveUpdate(
            title = metaInfo.title ?: "",
            description = metaInfo.description ?: ""
        )
    }
}
