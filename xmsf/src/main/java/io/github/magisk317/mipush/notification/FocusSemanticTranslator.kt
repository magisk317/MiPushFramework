package io.github.magisk317.mipush.notification

import android.app.Notification
import android.content.Context
import android.os.Build
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.magisk317.mipush.common.NotificationStyle
import org.json.JSONObject

/**
 * Translates MIUI/HyperIsland focus payloads into platform notification semantics.
 *
 * MIUI focus extras stay on the private MIUI/SystemUI path. Non-MIUI devices only
 * receive native Android surfaces, and only progress-like focus semantics become
 * Android 16+ Live Updates.
 */
object FocusSemanticTranslator {
    private const val MAX_SHORT_TEXT = 15

    data class Capabilities(
        val supportsMiuiFocusExtras: Boolean,
        val supportsNativeLiveUpdates: Boolean,
        val supportsSemanticColors: Boolean,
    ) {
        companion object {
            fun current(): Capabilities {
                val supportsNativeLiveUpdates = Build.VERSION.SDK_INT >= Build.VERSION_CODES.BAKLAVA
                return Capabilities(
                    supportsMiuiFocusExtras = MIUIUtils.isMIUI(),
                    supportsNativeLiveUpdates = supportsNativeLiveUpdates,
                    supportsSemanticColors = supportsNativeLiveUpdates &&
                        Build.VERSION.SDK_INT_FULL >= Build.VERSION_CODES_FULL.BAKLAVA_1,
                )
            }
        }
    }

    data class FocusSemantic(
        val category: LiveUpdateDetector.ProgressCategory,
        val style: NotificationStyle,
        val progressPercent: Int?,
        val progressText: String?,
        val startLabel: String?,
        val endLabel: String?,
        val trackerLabel: String?,
        val semanticStyle: SemanticStyle,
        val source: Source,
    ) {
        val isProgressLike: Boolean
            get() = style == NotificationStyle.PROGRESS ||
                category != LiveUpdateDetector.ProgressCategory.UNKNOWN

        fun toDetectionResult(): LiveUpdateDetector.DetectionResult? {
            if (!isProgressLike) return null
            return LiveUpdateDetector.DetectionResult(
                isProgress = true,
                category = category.takeIf { it != LiveUpdateDetector.ProgressCategory.UNKNOWN }
                    ?: LiveUpdateDetector.ProgressCategory.GENERIC_PROGRESS,
                progressPercent = progressPercent,
                progressText = progressText,
                startLabel = startLabel,
                endLabel = endLabel,
                trackerLabel = trackerLabel,
            )
        }
    }

    enum class Source {
        DETECTED,
        CONFIGURED_FOCUS,
        GENERATED_FOCUS,
    }

    enum class SemanticStyle {
        INFO,
        SAFE,
        CAUTION,
        DANGER,
        UNSPECIFIED,
    }

    data class Plan(
        val semantic: FocusSemantic?,
        val nativeDetection: LiveUpdateDetector.DetectionResult?,
        val attachMiuiFocusExtras: Boolean,
        val allowIslandProxy: Boolean,
        val reason: String,
    ) {
        val useNativeProgress: Boolean
            get() = nativeDetection?.isProgress == true
    }

    fun plan(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
        configuredFocusParam: String?,
        generatedFocusParam: String?,
        generatedFocusCandidate: Boolean,
        capabilities: Capabilities = Capabilities.current(),
    ): Plan {
        val detected = LiveUpdateDetector.detect(context, metaInfo, packageName)
        val semantic = parseFocusParam(configuredFocusParam, Source.CONFIGURED_FOCUS)
            ?: detected.takeIf { it.isProgress }?.toSemantic(Source.DETECTED)
            ?: parseFocusParam(generatedFocusParam, Source.GENERATED_FOCUS)

        val attachMiuiFocusExtras = capabilities.supportsMiuiFocusExtras &&
            generatedFocusCandidate &&
            !configuredFocusParam.isNullOrBlank()
        val allowIslandProxy = capabilities.supportsMiuiFocusExtras &&
            generatedFocusCandidate &&
            configuredFocusParam.isNullOrBlank()
        val nativeDetection = semantic
            ?.takeUnless { attachMiuiFocusExtras || allowIslandProxy }
            ?.takeIf { it.isProgressLike }
            ?.toDetectionResult()

        return Plan(
            semantic = semantic,
            nativeDetection = nativeDetection,
            attachMiuiFocusExtras = attachMiuiFocusExtras,
            allowIslandProxy = allowIslandProxy,
            reason = when {
                attachMiuiFocusExtras -> "miui_focus_extras"
                nativeDetection?.isProgress == true -> if (capabilities.supportsNativeLiveUpdates) {
                    "native_live_update"
                } else {
                    "fallback_progress"
                }
                allowIslandProxy -> "miui_island_proxy"
                else -> "standard_notification"
            },
        )
    }

    fun semanticStyleForCategory(category: LiveUpdateDetector.ProgressCategory): SemanticStyle {
        return when (category) {
            LiveUpdateDetector.ProgressCategory.DELIVERY,
            LiveUpdateDetector.ProgressCategory.RIDE_HAILING,
            LiveUpdateDetector.ProgressCategory.LOGISTICS,
            LiveUpdateDetector.ProgressCategory.DOWNLOAD,
            LiveUpdateDetector.ProgressCategory.TRAVEL,
            LiveUpdateDetector.ProgressCategory.NAVIGATION,
            LiveUpdateDetector.ProgressCategory.TIMER,
            LiveUpdateDetector.ProgressCategory.CALL,
            LiveUpdateDetector.ProgressCategory.GENERIC_PROGRESS -> SemanticStyle.INFO
            LiveUpdateDetector.ProgressCategory.UNKNOWN -> SemanticStyle.UNSPECIFIED
        }
    }

    fun toPlatformSemanticStyle(style: SemanticStyle): Int {
        return when (style) {
            SemanticStyle.INFO -> Notification.SEMANTIC_STYLE_INFO
            SemanticStyle.SAFE -> Notification.SEMANTIC_STYLE_SAFE
            SemanticStyle.CAUTION -> Notification.SEMANTIC_STYLE_CAUTION
            SemanticStyle.DANGER -> Notification.SEMANTIC_STYLE_DANGER
            SemanticStyle.UNSPECIFIED -> Notification.SEMANTIC_STYLE_UNSPECIFIED
        }
    }

    private fun LiveUpdateDetector.DetectionResult.toSemantic(source: Source): FocusSemantic {
        return FocusSemantic(
            category = category,
            style = NotificationStyle.PROGRESS,
            progressPercent = progressPercent,
            progressText = progressText,
            startLabel = startLabel,
            endLabel = endLabel,
            trackerLabel = trackerLabel,
            semanticStyle = semanticStyleForCategory(category),
            source = source,
        )
    }

    private fun parseFocusParam(focusParam: String?, source: Source): FocusSemantic? {
        if (focusParam.isNullOrBlank()) return null
        return runCatching {
            val root = JSONObject(focusParam)
            val paramV2 = root.optJSONObject("param_v2") ?: root
            val text = listOf(
                paramV2.optString("ticker"),
                paramV2.optString("businessName"),
                collectNestedText(paramV2),
            ).filter { it.isNotBlank() }.joinToString(" ")
            val style = resolveStyle(paramV2, text)
            val category = resolveCategory(text, style)
            val progress = resolveProgress(paramV2, text)
            val progressText = text.takeIf { it.isNotBlank() }
            val tracker = resolveTrackerLabel(paramV2, progressText, category)
            val (startLabel, endLabel) = labelsFor(category, progressText)
            FocusSemantic(
                category = category,
                style = style,
                progressPercent = progress,
                progressText = progressText,
                startLabel = startLabel,
                endLabel = endLabel,
                trackerLabel = tracker,
                semanticStyle = resolveSemanticStyle(text, category),
                source = source,
            )
        }.getOrNull()
    }

    private fun collectNestedText(json: JSONObject): String {
        val values = mutableListOf<String>()
        fun visit(value: Any?) {
            when (value) {
                is JSONObject -> {
                    val keys = value.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        if (key.contains("title", ignoreCase = true) ||
                            key.contains("content", ignoreCase = true) ||
                            key.contains("text", ignoreCase = true) ||
                            key.contains("label", ignoreCase = true)
                        ) {
                            value.optString(key).takeIf { it.isNotBlank() }?.let(values::add)
                        }
                        visit(value.opt(key))
                    }
                }
                is org.json.JSONArray -> {
                    for (index in 0 until value.length()) {
                        visit(value.opt(index))
                    }
                }
            }
        }
        visit(json)
        return values.distinct().joinToString(" ")
    }

    private fun resolveStyle(paramV2: JSONObject, text: String): NotificationStyle {
        return when {
            paramV2.has("progressBar") ||
                paramV2.has("multiProgressInfo") ||
                text.contains("progress", ignoreCase = true) ||
                text.contains("进度") -> NotificationStyle.PROGRESS
            paramV2.has("chatInfo") -> NotificationStyle.MESSAGE
            paramV2.has("coverInfo") -> NotificationStyle.MEDIA
            paramV2.has("highlightInfo") -> NotificationStyle.ALERT
            paramV2.has("highlightInfoV3") -> NotificationStyle.PROMO
            else -> NotificationStyle.GENERAL
        }
    }

    private fun resolveCategory(text: String, style: NotificationStyle): LiveUpdateDetector.ProgressCategory {
        val detected = detectCategoryFromText(text)
        if (detected != LiveUpdateDetector.ProgressCategory.UNKNOWN) {
            return detected
        }
        return if (style == NotificationStyle.PROGRESS) {
            LiveUpdateDetector.ProgressCategory.GENERIC_PROGRESS
        } else {
            LiveUpdateDetector.ProgressCategory.UNKNOWN
        }
    }

    private fun resolveProgress(paramV2: JSONObject, text: String): Int? {
        val fromJson = paramV2.optJSONObject("progressBar")
            ?.takeIf { it.has("progress") }
            ?.optInt("progress")
        return fromJson?.coerceIn(0, 100) ?: extractProgressPercent(text)
    }

    private fun resolveTrackerLabel(
        paramV2: JSONObject,
        progressText: String?,
        category: LiveUpdateDetector.ProgressCategory,
    ): String? {
        val label = paramV2.optJSONObject("hintInfo")?.optString("title")
            ?.takeIf { it.isNotBlank() }
        return label ?: progressText?.take(MAX_SHORT_TEXT)
            ?: defaultTrackerLabel(category)
    }

    private fun resolveSemanticStyle(
        text: String,
        category: LiveUpdateDetector.ProgressCategory,
    ): SemanticStyle {
        return when {
            text.contains("危险") || text.contains("失败") || text.contains("danger", ignoreCase = true) ->
                SemanticStyle.DANGER
            text.contains("警告") || text.contains("延误") || text.contains("caution", ignoreCase = true) ->
                SemanticStyle.CAUTION
            text.contains("完成") || text.contains("已送达") || text.contains("safe", ignoreCase = true) ->
                SemanticStyle.SAFE
            else -> semanticStyleForCategory(category)
        }
    }

    private fun detectCategoryFromText(text: String): LiveUpdateDetector.ProgressCategory {
        val lower = text.lowercase()
        var bestCategory = LiveUpdateDetector.ProgressCategory.UNKNOWN
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
        return if (bestScore >= 2) bestCategory else LiveUpdateDetector.ProgressCategory.UNKNOWN
    }

    private fun extractProgressPercent(text: String): Int? {
        Regex("(\\d{1,3})%").find(text)?.let {
            return it.groupValues[1].toIntOrNull()?.coerceIn(0, 100)
        }
        Regex("(\\d+)\\s*/\\s*(\\d+)").find(text)?.let {
            val current = it.groupValues[1].toIntOrNull() ?: return null
            val total = it.groupValues[2].toIntOrNull() ?: return null
            if (total > 0) {
                return ((current * 100) / total).coerceIn(0, 100)
            }
        }
        return null
    }

    private fun labelsFor(
        category: LiveUpdateDetector.ProgressCategory,
        progressText: String?,
    ): Pair<String?, String?> {
        return when (category) {
            LiveUpdateDetector.ProgressCategory.DELIVERY -> "商家" to "目的地"
            LiveUpdateDetector.ProgressCategory.RIDE_HAILING -> "上车点" to "目的地"
            LiveUpdateDetector.ProgressCategory.LOGISTICS -> "发件地" to "收件地"
            LiveUpdateDetector.ProgressCategory.DOWNLOAD -> "开始" to "完成"
            LiveUpdateDetector.ProgressCategory.TRAVEL -> "出发" to "到达"
            LiveUpdateDetector.ProgressCategory.NAVIGATION -> "当前位置" to "目的地"
            LiveUpdateDetector.ProgressCategory.TIMER -> "开始" to "结束"
            LiveUpdateDetector.ProgressCategory.CALL -> "通话" to "结束"
            LiveUpdateDetector.ProgressCategory.GENERIC_PROGRESS -> "开始" to "完成"
            LiveUpdateDetector.ProgressCategory.UNKNOWN -> null to null
        }.let { labels ->
            labels.takeIf { !progressText.isNullOrBlank() || category != LiveUpdateDetector.ProgressCategory.UNKNOWN }
        } ?: (null to null)
    }

    private fun defaultTrackerLabel(category: LiveUpdateDetector.ProgressCategory): String? {
        return when (category) {
            LiveUpdateDetector.ProgressCategory.DELIVERY -> "配送中"
            LiveUpdateDetector.ProgressCategory.RIDE_HAILING -> "接驾中"
            LiveUpdateDetector.ProgressCategory.LOGISTICS -> "运输中"
            LiveUpdateDetector.ProgressCategory.DOWNLOAD -> "下载中"
            LiveUpdateDetector.ProgressCategory.TRAVEL -> "行程中"
            LiveUpdateDetector.ProgressCategory.NAVIGATION -> "导航中"
            LiveUpdateDetector.ProgressCategory.TIMER -> "计时中"
            LiveUpdateDetector.ProgressCategory.CALL -> "通话中"
            LiveUpdateDetector.ProgressCategory.GENERIC_PROGRESS -> "进行中"
            LiveUpdateDetector.ProgressCategory.UNKNOWN -> null
        }
    }

    private val FOCUS_CATEGORY_KEYWORDS = mapOf(
        LiveUpdateDetector.ProgressCategory.DELIVERY to listOf(
            "骑手", "配送", "外卖", "取餐", "送餐", "商家", "预计送达",
            "delivery", "rider", "order", "food", "restaurant",
        ),
        LiveUpdateDetector.ProgressCategory.RIDE_HAILING to listOf(
            "司机", "车辆", "接驾", "行程", "预计到达", "快车", "出租车",
            "driver", "arriving", "trip", "ride", "pickup", "taxi",
        ),
        LiveUpdateDetector.ProgressCategory.LOGISTICS to listOf(
            "物流", "快递", "包裹", "运输中", "派送中", "待取件",
            "logistics", "package", "shipping", "delivered", "transit",
        ),
        LiveUpdateDetector.ProgressCategory.DOWNLOAD to listOf(
            "下载", "安装", "更新", "升级", "download", "installing", "updating",
        ),
        LiveUpdateDetector.ProgressCategory.TRAVEL to listOf(
            "航班", "登机", "起飞", "火车", "高铁", "检票", "进站",
            "flight", "boarding", "departure", "arrival", "check-in",
        ),
        LiveUpdateDetector.ProgressCategory.NAVIGATION to listOf(
            "导航", "路线", "路口", "目的地", "剩余路程", "直行", "左转", "右转",
            "navigation", "route", "turn", "eta", "destination",
        ),
        LiveUpdateDetector.ProgressCategory.TIMER to listOf(
            "计时", "倒计时", "还剩", "剩余时间", "秒表", "番茄钟",
            "timer", "countdown", "stopwatch", "remaining",
        ),
        LiveUpdateDetector.ProgressCategory.CALL to listOf(
            "通话", "来电", "呼叫", "语音通话", "视频通话",
            "call", "calling", "voice call", "video call",
        ),
    )
}
