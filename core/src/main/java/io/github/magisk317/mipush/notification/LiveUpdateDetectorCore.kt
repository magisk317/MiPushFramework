package io.github.magisk317.mipush.notification

import java.util.regex.Pattern

/**
 * Pure-logic core of Live Update detection.
 *
 * Detects progress-style (Live Update) notifications from push message content
 * using only platform-independent String parameters. No Android Context or
 * protocol-specific types are used.
 *
 * Categories detected:
 * - Delivery / food delivery (外卖配送)
 * - Ride-hailing / taxi (打车)
 * - Logistics / package tracking (物流)
 * - Download / installation (下载安装)
 * - Travel / itinerary (行程)
 */
object LiveUpdateDetectorCore {

    /**
     * Progress category classification for Live Update notifications.
     */
    enum class ProgressCategory(val label: String) {
        DELIVERY("delivery"),
        RIDE_HAILING("ride_hailing"),
        LOGISTICS("logistics"),
        DOWNLOAD("download"),
        TRAVEL("travel"),
        NAVIGATION("navigation"),
        TIMER("timer"),
        CALL("call"),
        GENERIC_PROGRESS("progress"),
        UNKNOWN("unknown");

        fun isTransportRelated(): Boolean =
            this == DELIVERY || this == RIDE_HAILING || this == LOGISTICS || this == NAVIGATION
    }

    /**
     * Platform-independent input for Live Update detection.
     * Converts Android-specific types (Context, PushMetaInfo) into plain strings.
     */
    data class DetectionInput(
        val title: String,
        val description: String,
        val packageName: String
    )

    /**
     * Result of Live Update detection analysis.
     */
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
    internal val PERCENT_PATTERN: Pattern = Pattern.compile("(\\d{1,3})%")
    internal val FRACTION_PATTERN: Pattern = Pattern.compile("(\\d+)\\s*/\\s*(\\d+)")

    // Category detection keywords (title + description combined)
    internal val CATEGORY_KEYWORDS: Map<ProgressCategory, List<String>> = mapOf(
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
        ),
        ProgressCategory.NAVIGATION to listOf(
            "导航", "路线", "路口", "目的地", "剩余路程", "预计到达",
            "直行", "左转", "右转", "掉头", "限速",
            "navigation", "route", "turn", "eta", "destination"
        ),
        ProgressCategory.TIMER to listOf(
            "计时", "倒计时", "还剩", "剩余时间", "秒表", "番茄钟",
            "timer", "countdown", "stopwatch", "remaining"
        ),
        ProgressCategory.CALL to listOf(
            "通话", "来电", "呼叫", "语音通话", "视频通话", "会议通话",
            "call", "calling", "voice call", "video call", "meeting call"
        )
    )

    // Progress state keywords that indicate an active ongoing process
    internal val ACTIVE_PROGRESS_KEYWORDS: List<String> = listOf(
        "正在", "配送中", "运输中", "下载中", "进行中", "处理中",
        "即将", "预计", "约", "大概", "还有",
        "progress", "ongoing", "in progress", "processing"
    )

    /**
     * Perform full Live Update detection on the given input.
     *
     * @param input Platform-independent detection input
     * @return DetectionResult with category, progress percentage, and labels
     */
    fun detect(input: DetectionInput): DetectionResult {
        val combined = "${input.title} ${input.description}"

        // Check if this package is explicitly blacklisted from Live Updates
        if (isPackageBlacklisted(input.packageName)) {
            return DetectionResult.NONE
        }

        // Detect category from keywords
        val category = detectCategory(combined)
        if (category == ProgressCategory.UNKNOWN) {
            return DetectionResult.NONE
        }

        // Check if the message has active progress indicators
        if (!hasActiveProgressIndicator(combined)) {
            return DetectionResult.NONE
        }

        val progressPercent = extractProgressPercent(combined)
        val (startLabel, endLabel, trackerLabel) = extractLabels(category, input.title, input.description)

        return DetectionResult(
            isProgress = true,
            category = category,
            progressPercent = progressPercent,
            progressText = input.description.takeIf { it.isNotBlank() },
            startLabel = startLabel,
            endLabel = endLabel,
            trackerLabel = trackerLabel
        )
    }

    /**
     * Quick check: does this message look like it could be a Live Update?
     * Use this for early-return in hot paths.
     */
    fun isPotentialLiveUpdate(title: String, description: String): Boolean {
        val combined = "$title $description"
        return detectCategory(combined) != ProgressCategory.UNKNOWN &&
            hasActiveProgressIndicator(combined)
    }

    /**
     * Detect the progress category from combined title+description text.
     * Returns [ProgressCategory.UNKNOWN] if no category matches with sufficient confidence.
     */
    internal fun detectCategory(text: String): ProgressCategory {
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

    /**
     * Check if the text contains active progress indicators (percentage, fraction,
     * progress keywords, time/distance estimates).
     */
    internal fun hasActiveProgressIndicator(text: String): Boolean {
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

    /**
     * Extract progress percentage from text.
     * Supports both "XX%" format and "X/Y" fraction format.
     *
     * @return Progress percentage (0-100) or null if not found
     */
    internal fun extractProgressPercent(text: String): Int? {
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

    /**
     * Extract start/end/tracker labels based on category and message content.
     *
     * @return Triple of (startLabel, endLabel, trackerLabel)
     */
    internal fun extractLabels(
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
            ProgressCategory.NAVIGATION -> Triple(
                "当前位置",
                "目的地",
                extractTrackerFromDescription(description) ?: "导航中"
            )
            ProgressCategory.TIMER -> Triple(
                "开始",
                "结束",
                extractTrackerFromDescription(description) ?: "计时中"
            )
            ProgressCategory.CALL -> Triple(
                "通话",
                "结束",
                extractTrackerFromDescription(description) ?: "通话中"
            )
            ProgressCategory.GENERIC_PROGRESS -> Triple(
                "开始",
                "完成",
                extractTrackerFromDescription(description) ?: "进行中"
            )
            else -> Triple(null, null, null)
        }
    }

    /**
     * Check if a package is blacklisted from Live Update detection.
     * Blacklisted packages (e.g., chat apps) should never use Live Update style.
     */
    internal fun isPackageBlacklisted(packageName: String): Boolean {
        val blacklist = setOf(
            "com.tencent.mm",      // WeChat
            "com.tencent.mobileqq", // QQ
            "com.alibaba.android.rimet", // DingTalk
        )
        return packageName in blacklist
    }

    private fun extractTrackerFromDescription(description: String): String? {
        // Extract the most meaningful short phrase (first sentence or key phrase)
        val sentences = description.split(Regex("[。！？.!?]"))
        val firstMeaningful = sentences.firstOrNull { it.length >= 3 }?.trim()
        return firstMeaningful?.take(20)
    }
}
