package io.github.magisk317.mipush.notification

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/** Pure focus-payload parsing and planning. Android notification construction stays in adapters. */
object FocusSemanticCore {
    enum class Style { GENERAL, MESSAGE, MEDIA, ALERT, PROMO, PROGRESS }
    enum class SemanticStyle { INFO, SAFE, CAUTION, DANGER, UNSPECIFIED }
    enum class Source { DETECTED, CONFIGURED_FOCUS, GENERATED_FOCUS }

    data class Semantic(
        val category: LiveUpdateDetectorCore.ProgressCategory,
        val style: Style,
        val progressPercent: Int?,
        val progressText: String?,
        val startLabel: String?,
        val endLabel: String?,
        val trackerLabel: String?,
        val semanticStyle: SemanticStyle,
        val source: Source,
    ) {
        val isProgressLike: Boolean
            get() = style == Style.PROGRESS || category != LiveUpdateDetectorCore.ProgressCategory.UNKNOWN
    }

    data class Capabilities(
        val supportsMiuiFocusExtras: Boolean,
        val supportsNativeLiveUpdates: Boolean,
    )

    data class Plan(
        val semantic: Semantic?,
        val attachMiuiFocusExtras: Boolean,
        val allowIslandProxy: Boolean,
        val reason: String,
    )

    fun plan(
        detected: LiveUpdateDetectorCore.DetectionResult?,
        configuredFocusParam: String?,
        generatedFocusParam: String?,
        generatedFocusCandidate: Boolean,
        capabilities: Capabilities,
    ): Plan {
        val semantic = parse(configuredFocusParam, Source.CONFIGURED_FOCUS)
            ?: detected?.takeIf { it.isProgress }?.toSemantic(Source.DETECTED)
            ?: parse(generatedFocusParam, Source.GENERATED_FOCUS)
        val configured = !configuredFocusParam.isNullOrBlank()
        val attach = capabilities.supportsMiuiFocusExtras && configured
        val proxy = capabilities.supportsMiuiFocusExtras && generatedFocusCandidate && !configured
        val native = semantic?.isProgressLike == true && !attach && !proxy
        return Plan(
            semantic = semantic,
            attachMiuiFocusExtras = attach,
            allowIslandProxy = proxy,
            reason = when {
                attach -> "miui_focus_extras"
                native && capabilities.supportsNativeLiveUpdates -> "native_live_update"
                native -> "fallback_progress"
                proxy -> "miui_island_proxy"
                else -> "standard_notification"
            },
        )
    }

    fun semanticStyleForCategory(category: LiveUpdateDetectorCore.ProgressCategory): SemanticStyle = when {
        category == LiveUpdateDetectorCore.ProgressCategory.UNKNOWN -> SemanticStyle.UNSPECIFIED
        else -> SemanticStyle.INFO
    }

    fun toPlatformSemanticStyle(style: SemanticStyle): Int = when (style) {
        SemanticStyle.UNSPECIFIED -> 0
        SemanticStyle.INFO -> 1
        SemanticStyle.SAFE -> 2
        SemanticStyle.CAUTION -> 3
        SemanticStyle.DANGER -> 4
    }

    private fun LiveUpdateDetectorCore.DetectionResult.toSemantic(source: Source) = Semantic(
        category = category,
        style = Style.PROGRESS,
        progressPercent = progressPercent,
        progressText = progressText,
        startLabel = startLabel,
        endLabel = endLabel,
        trackerLabel = trackerLabel,
        semanticStyle = semanticStyleForCategory(category),
        source = source,
    )

    private fun parse(value: String?, source: Source): Semantic? = runCatching {
        if (value.isNullOrBlank()) return null
        val root = Json.parseToJsonElement(value) as? JsonObject ?: return null
        val body = root[PARAM_V2] as? JsonObject ?: root
        val text = listOf(body.text("ticker"), body.text("businessName"), nestedText(body))
            .filterNotNull()
            .filter(String::isNotBlank)
            .joinToString(" ")
        val style = resolveStyle(body, text)
        val category = resolveCategory(text, style)
        val progressPercent = ((body["progressBar"] as? JsonObject)?.get("progress") as? JsonPrimitive)
            ?.content
            ?.toIntOrNull()
            ?.coerceIn(0, 100)
            ?: extractProgressPercent(text)
        val progressText = text.takeIf(String::isNotBlank)
        Semantic(
            category = category,
            style = style,
            progressPercent = progressPercent,
            progressText = progressText,
            startLabel = labels(category).first,
            endLabel = labels(category).second,
            trackerLabel = (body["hintInfo"] as? JsonObject)?.text("title")
                ?: progressText?.take(MAX_SHORT_TEXT)
                ?: defaultTrackerLabel(category),
            semanticStyle = resolveSemanticStyle(text, category),
            source = source,
        )
    }.getOrNull()

    private fun resolveStyle(body: JsonObject, text: String): Style = when {
        body.containsKey("progressBar") || body.containsKey("multiProgressInfo") ||
            text.contains("progress", ignoreCase = true) || text.contains("进度") -> Style.PROGRESS
        body.containsKey("chatInfo") -> Style.MESSAGE
        body.containsKey("coverInfo") -> Style.MEDIA
        body.containsKey("highlightInfo") -> Style.ALERT
        body.containsKey("highlightInfoV3") -> Style.PROMO
        else -> Style.GENERAL
    }

    private fun resolveCategory(
        text: String,
        style: Style,
    ): LiveUpdateDetectorCore.ProgressCategory {
        val lower = text.lowercase()
        var bestCategory = LiveUpdateDetectorCore.ProgressCategory.UNKNOWN
        var bestScore = 0
        for ((category, keywords) in FOCUS_CATEGORY_KEYWORDS) {
            var score = 0
            for (keyword in keywords) {
                if (lower.contains(keyword.lowercase())) {
                    score += if (keyword.length >= 4) 2 else 1
                }
            }
            if (score > bestScore) {
                bestScore = score
                bestCategory = category
            }
        }
        return if (bestScore >= 2) {
            bestCategory
        } else if (style == Style.PROGRESS) {
            LiveUpdateDetectorCore.ProgressCategory.GENERIC_PROGRESS
        } else {
            LiveUpdateDetectorCore.ProgressCategory.UNKNOWN
        }
    }

    private fun extractProgressPercent(text: String): Int? {
        PERCENT_PATTERN.find(text)?.let { match ->
            return match.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
        }
        FRACTION_PATTERN.find(text)?.let { match ->
            val current = match.groupValues[1].toLongOrNull() ?: return null
            val total = match.groupValues[2].toLongOrNull() ?: return null
            if (total > 0L) return ((current * 100L) / total).coerceIn(0L, 100L).toInt()
        }
        return null
    }

    private fun JsonObject.text(key: String): String? =
        (this[key] as? JsonPrimitive)?.content?.takeIf(String::isNotBlank)

    private fun nestedText(root: JsonElement): String = buildList {
        fun visit(value: JsonElement) {
            when (value) {
                is JsonObject -> value.forEach { (key, child) ->
                    if (key.contains("title", true) || key.contains("content", true) ||
                        key.contains("text", true) || key.contains("label", true)
                    ) {
                        (child as? JsonPrimitive)
                            ?.takeUnless { it is JsonNull }
                            ?.content
                            ?.takeIf(String::isNotBlank)
                            ?.let(::add)
                    }
                    visit(child)
                }
                is JsonArray -> value.forEach(::visit)
                else -> Unit
            }
        }
        visit(root)
    }.distinct().joinToString(" ")

    private fun labels(category: LiveUpdateDetectorCore.ProgressCategory): Pair<String?, String?> = when (category) {
        LiveUpdateDetectorCore.ProgressCategory.DELIVERY -> "商家" to "目的地"
        LiveUpdateDetectorCore.ProgressCategory.RIDE_HAILING -> "上车点" to "目的地"
        LiveUpdateDetectorCore.ProgressCategory.LOGISTICS -> "发件地" to "收件地"
        LiveUpdateDetectorCore.ProgressCategory.DOWNLOAD -> "开始" to "完成"
        LiveUpdateDetectorCore.ProgressCategory.TRAVEL -> "出发" to "到达"
        LiveUpdateDetectorCore.ProgressCategory.NAVIGATION -> "当前位置" to "目的地"
        LiveUpdateDetectorCore.ProgressCategory.TIMER -> "开始" to "结束"
        LiveUpdateDetectorCore.ProgressCategory.CALL -> "通话" to "结束"
        LiveUpdateDetectorCore.ProgressCategory.GENERIC_PROGRESS -> "开始" to "完成"
        else -> null to null
    }

    private fun defaultTrackerLabel(category: LiveUpdateDetectorCore.ProgressCategory): String? = when (category) {
        LiveUpdateDetectorCore.ProgressCategory.DELIVERY -> "配送中"
        LiveUpdateDetectorCore.ProgressCategory.RIDE_HAILING -> "接驾中"
        LiveUpdateDetectorCore.ProgressCategory.LOGISTICS -> "运输中"
        LiveUpdateDetectorCore.ProgressCategory.DOWNLOAD -> "下载中"
        LiveUpdateDetectorCore.ProgressCategory.TRAVEL -> "行程中"
        LiveUpdateDetectorCore.ProgressCategory.NAVIGATION -> "导航中"
        LiveUpdateDetectorCore.ProgressCategory.TIMER -> "计时中"
        LiveUpdateDetectorCore.ProgressCategory.CALL -> "通话中"
        LiveUpdateDetectorCore.ProgressCategory.GENERIC_PROGRESS -> "进行中"
        LiveUpdateDetectorCore.ProgressCategory.UNKNOWN -> null
    }

    private fun resolveSemanticStyle(
        text: String,
        category: LiveUpdateDetectorCore.ProgressCategory,
    ): SemanticStyle = when {
        text.contains("危险") || text.contains("失败") || text.contains("danger", true) -> SemanticStyle.DANGER
        text.contains("警告") || text.contains("延误") || text.contains("caution", true) -> SemanticStyle.CAUTION
        text.contains("完成") || text.contains("已送达") || text.contains("safe", true) -> SemanticStyle.SAFE
        else -> semanticStyleForCategory(category)
    }

    private const val MAX_SHORT_TEXT = 15
    private const val PARAM_V2 = "param_v2"
    private val PERCENT_PATTERN = Regex("(\\d{1,3})%")
    private val FRACTION_PATTERN = Regex("(\\d+)\\s*/\\s*(\\d+)")
    private val FOCUS_CATEGORY_KEYWORDS = mapOf(
        LiveUpdateDetectorCore.ProgressCategory.DELIVERY to listOf(
            "骑手", "配送", "外卖", "取餐", "送餐", "商家", "预计送达",
            "delivery", "rider", "order", "food", "restaurant",
        ),
        LiveUpdateDetectorCore.ProgressCategory.RIDE_HAILING to listOf(
            "司机", "车辆", "接驾", "行程", "预计到达", "快车", "出租车",
            "driver", "arriving", "trip", "ride", "pickup", "taxi",
        ),
        LiveUpdateDetectorCore.ProgressCategory.LOGISTICS to listOf(
            "物流", "快递", "包裹", "运输中", "派送中", "待取件",
            "logistics", "package", "shipping", "delivered", "transit",
        ),
        LiveUpdateDetectorCore.ProgressCategory.DOWNLOAD to listOf(
            "下载", "安装", "更新", "升级", "download", "installing", "updating",
        ),
        LiveUpdateDetectorCore.ProgressCategory.TRAVEL to listOf(
            "航班", "登机", "起飞", "火车", "高铁", "检票", "进站",
            "flight", "boarding", "departure", "arrival", "check-in",
        ),
        LiveUpdateDetectorCore.ProgressCategory.NAVIGATION to listOf(
            "导航", "路线", "路口", "目的地", "剩余路程", "直行", "左转", "右转",
            "navigation", "route", "turn", "eta", "destination",
        ),
        LiveUpdateDetectorCore.ProgressCategory.TIMER to listOf(
            "计时", "倒计时", "还剩", "剩余时间", "秒表", "番茄钟",
            "timer", "countdown", "stopwatch", "remaining",
        ),
        LiveUpdateDetectorCore.ProgressCategory.CALL to listOf(
            "通话", "来电", "呼叫", "语音通话", "视频通话",
            "call", "calling", "voice call", "video call",
        ),
    )
}
