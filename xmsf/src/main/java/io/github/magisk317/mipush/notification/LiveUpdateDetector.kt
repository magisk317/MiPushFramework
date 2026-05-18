package io.github.magisk317.mipush.notification

import android.content.Context
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.aakira.napier.Napier
import java.util.regex.Pattern

/**
 * Detects progress-style (Live Update) notifications from push message content.
 *
 * Framework-side detection for messages that should be rendered as Live Updates
 * (Android 16+ ProgressStyle / promoted ongoing notifications).
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

    enum class ProgressCategory(val label: String) {
        DELIVERY("delivery"),
        RIDE_HAILING("ride_hailing"),
        LOGISTICS("logistics"),
        DOWNLOAD("download"),
        TRAVEL("travel"),
        UNKNOWN("unknown");

        fun isTransportRelated(): Boolean = this == DELIVERY || this == RIDE_HAILING || this == LOGISTICS
    }

    data class DetectionResult(
        val isProgress: Boolean,
        val category: ProgressCategory,
        val progressPercent: Int?,
        val progressText: String?,
        val startLabel: String?,
        val endLabel: String?,
        val trackerLabel: String?
    ) {
        companion object {
            val NONE = DetectionResult(false, ProgressCategory.UNKNOWN, null, null, null, null, null)
        }
    }

    // Regex patterns for progress extraction
    private val PERCENT_PATTERN = Pattern.compile("(\\d{1,3})%")
    private val FRACTION_PATTERN = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)")

    // Category detection keywords (title + description combined)
    private val CATEGORY_KEYWORDS = mapOf(
        ProgressCategory.DELIVERY to listOf(
            "骑手", "配送", "外卖", "取餐", "送餐", "商家", "预计送达",
            "正在配送", "骑手已接单", "骑手已取餐", "骑手距您", "骑手到达",
            "delivery", "rider", "order", "food", "restaurant"
        ),
        ProgressCategory.RIDE_HAILING to listOf(
            "司机", "车辆", "接驾", "行程", "预计到达", "正在前往",
            "司机已到达", "您的专车", "快车", "出租车", "顺风车",
            "driver", "arriving", "trip", "ride", "pickup", "taxi"
        ),
        ProgressCategory.LOGISTICS to listOf(
            "物流", "快递", "包裹", "运输中", "已揽收", "派送中",
            "已签收", "待取件", "到达", "离开", "转运",
            "logistics", "package", "shipping", "delivered", "transit"
        ),
        ProgressCategory.DOWNLOAD to listOf(
            "下载", "安装", "更新", "升级", "正在下载", "下载中",
            "download", "installing", "updating", "downloading"
        ),
        ProgressCategory.TRAVEL to listOf(
            "航班", "登机", "起飞", "到达", "延误", "值机",
            "火车", "高铁", "动车", "检票", "进站",
            "flight", "boarding", "departure", "arrival", "check-in"
        )
    )

    // Progress state keywords that indicate an active ongoing process
    private val ACTIVE_PROGRESS_KEYWORDS = listOf(
        "正在", "配送中", "运输中", "下载中", "进行中", "处理中",
        "即将", "预计", "约", "大概", "还有",
        "progress", "ongoing", "in progress", "processing"
    )

    /**
     * Analyze a push message and determine if it represents a progress/Live Update notification.
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

        val title = metaInfo.title ?: ""
        val description = metaInfo.description ?: ""
        val combined = "$title $description"

        // Check if this package is explicitly blacklisted from Live Updates
        if (isPackageBlacklisted(packageName)) {
            return DetectionResult.NONE
        }

        // Detect category from keywords
        val category = detectCategory(combined)
        if (category == ProgressCategory.UNKNOWN) {
            return DetectionResult.NONE
        }

        // Check if the message has active progress indicators
        if (!hasActiveProgressIndicator(combined)) {
            Napier.d("category=$category but no active progress indicator, skipping Live Update pkg=$packageName", tag = TAG)
            return DetectionResult.NONE
        }

        val progressPercent = extractProgressPercent(combined)
        val (startLabel, endLabel, trackerLabel) = extractLabels(category, title, description)

        Napier.i(
            "Live Update detected pkg=$packageName category=$category progress=$progressPercent " +
                "title=$title description=$description",
            tag = TAG
        )

        return DetectionResult(
            isProgress = true,
            category = category,
            progressPercent = progressPercent,
            progressText = description.takeIf { it.isNotBlank() },
            startLabel = startLabel,
            endLabel = endLabel,
            trackerLabel = trackerLabel
        )
    }

    /**
     * Quick check: does this message look like it could be a Live Update?
     * Use this for early-return in hot paths.
     */
    @JvmStatic
    fun isPotentialLiveUpdate(metaInfo: PushMetaInfo?): Boolean {
        if (metaInfo == null) return false
        val combined = "${metaInfo.title ?: ""} ${metaInfo.description ?: ""}"
        return detectCategory(combined) != ProgressCategory.UNKNOWN &&
            hasActiveProgressIndicator(combined)
    }

    private fun detectCategory(text: String): ProgressCategory {
        val lower = text.lowercase()
        var bestCategory = ProgressCategory.UNKNOWN
        var bestScore = 0

        for ((category, keywords) in CATEGORY_KEYWORDS) {
            var score = 0
            for (keyword in keywords) {
                if (lower.contains(keyword.lowercase())) {
                    score++
                    // Higher weight for longer/more specific keywords
                    if (keyword.length >= 4) score++
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestCategory = category
            }
        }

        // Require at least 2 keyword matches (or 1 long keyword) for confidence
        return if (bestScore >= 2) bestCategory else ProgressCategory.UNKNOWN
    }

    private fun hasActiveProgressIndicator(text: String): Boolean {
        val lower = text.lowercase()

        // Has explicit progress percentage
        if (PERCENT_PATTERN.matcher(text).find()) return true
        if (FRACTION_PATTERN.matcher(text).find()) return true

        // Has active progress keywords
        for (keyword in ACTIVE_PROGRESS_KEYWORDS) {
            if (lower.contains(keyword.lowercase())) return true
        }

        // Contains time estimate (e.g., "5分钟", "10 mins")
        if (Regex("\\d+\\s*[分分钟秒秒小小时时minsec]|约\\d+|预计").containsMatchIn(text)) {
            return true
        }

        // Contains distance estimate (e.g., "500米", "1.2km")
        if (Regex("\\d+[\\.\\d]*\\s*[米千米公里mkm]|距您").containsMatchIn(text)) {
            return true
        }

        return false
    }

    private fun extractProgressPercent(text: String): Int? {
        val percentMatcher = PERCENT_PATTERN.matcher(text)
        if (percentMatcher.find()) {
            return percentMatcher.group(1)?.toIntOrNull()?.coerceIn(0, 100)
        }

        val fractionMatcher = FRACTION_PATTERN.matcher(text)
        if (fractionMatcher.find()) {
            val current = fractionMatcher.group(1)?.toIntOrNull() ?: return null
            val total = fractionMatcher.group(2)?.toIntOrNull() ?: return null
            if (total > 0) {
                return ((current * 100) / total).coerceIn(0, 100)
            }
        }

        return null
    }

    private fun extractLabels(
        category: ProgressCategory,
        title: String,
        description: String
    ): Triple<String?, String?, String?> {
        return when (category) {
            ProgressCategory.DELIVERY -> Triple(
                "商家",
                "目的地",
                extractTrackerFromDescription(description) ?: "配送中"
            )
            ProgressCategory.RIDE_HAILING -> Triple(
                "上车点",
                "目的地",
                extractTrackerFromDescription(description) ?: "接驾中"
            )
            ProgressCategory.LOGISTICS -> Triple(
                "发件地",
                "收件地",
                extractTrackerFromDescription(description) ?: "运输中"
            )
            ProgressCategory.DOWNLOAD -> Triple(
                "开始",
                "完成",
                extractTrackerFromDescription(description) ?: "下载中"
            )
            ProgressCategory.TRAVEL -> Triple(
                "出发",
                "到达",
                extractTrackerFromDescription(description) ?: "行程中"
            )
            else -> Triple(null, null, null)
        }
    }

    private fun extractTrackerFromDescription(description: String): String? {
        // Extract the most meaningful short phrase (first sentence or key phrase)
        val sentences = description.split(Regex("[。！？.!?]"))
        val firstMeaningful = sentences.firstOrNull { it.length >= 3 }?.trim()
        return firstMeaningful?.take(20)
    }

    private fun isPackageBlacklisted(packageName: String): Boolean {
        // Packages that should never use Live Update style (e.g., chat apps)
        val blacklist = setOf(
            "com.tencent.mm",      // WeChat
            "com.tencent.mobileqq", // QQ
            "com.alibaba.android.rimet", // DingTalk
        )
        return packageName in blacklist
    }
}
