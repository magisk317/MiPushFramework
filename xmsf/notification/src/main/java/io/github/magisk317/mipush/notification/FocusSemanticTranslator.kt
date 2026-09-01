package io.github.magisk317.mipush.notification

import android.content.Context
import android.os.Build
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.magisk317.mipush.notification.policy.NotificationStyle

/** Android/protocol facade for the platform-neutral focus semantic planner. */
object FocusSemanticTranslator {
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

        fun toDetectionResult(): LiveUpdateDetector.DetectionResult? =
            takeIf { isProgressLike }?.let {
                LiveUpdateDetector.DetectionResult(
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
    ): Plan = plan(
        detected = LiveUpdateDetector.detect(context, metaInfo, packageName),
        configuredFocusParam = configuredFocusParam,
        generatedFocusParam = generatedFocusParam,
        generatedFocusCandidate = generatedFocusCandidate,
        capabilities = capabilities,
    )

    fun plan(
        detected: LiveUpdateDetector.DetectionResult?,
        metaInfo: PushMetaInfo,
        packageName: String,
        configuredFocusParam: String?,
        generatedFocusParam: String?,
        generatedFocusCandidate: Boolean,
        capabilities: Capabilities,
    ): Plan = plan(
        detected = detected,
        configuredFocusParam = configuredFocusParam,
        generatedFocusParam = generatedFocusParam,
        generatedFocusCandidate = generatedFocusCandidate,
        capabilities = capabilities,
    )

    private fun plan(
        detected: LiveUpdateDetector.DetectionResult?,
        configuredFocusParam: String?,
        generatedFocusParam: String?,
        generatedFocusCandidate: Boolean,
        capabilities: Capabilities,
    ): Plan {
        val corePlan = FocusSemanticCore.plan(
            detected = detected?.toCoreDetection(),
            configuredFocusParam = configuredFocusParam,
            generatedFocusParam = generatedFocusParam,
            generatedFocusCandidate = generatedFocusCandidate,
            capabilities = FocusSemanticCore.Capabilities(
                supportsMiuiFocusExtras = capabilities.supportsMiuiFocusExtras,
                supportsNativeLiveUpdates = capabilities.supportsNativeLiveUpdates,
            ),
        )
        val semantic = corePlan.semantic?.toFacadeSemantic()
        val nativeDetection = semantic?.takeUnless {
            corePlan.attachMiuiFocusExtras || corePlan.allowIslandProxy
        }?.toDetectionResult()
        return Plan(
            semantic = semantic,
            nativeDetection = nativeDetection,
            attachMiuiFocusExtras = corePlan.attachMiuiFocusExtras,
            allowIslandProxy = corePlan.allowIslandProxy,
            reason = corePlan.reason,
        )
    }

    fun semanticStyleForCategory(category: LiveUpdateDetector.ProgressCategory): SemanticStyle =
        FocusSemanticCore.semanticStyleForCategory(category.toCoreCategory()).toFacadeSemanticStyle()

    fun toPlatformSemanticStyle(style: SemanticStyle): Int =
        FocusSemanticCore.toPlatformSemanticStyle(style.toCoreSemanticStyle())

    private fun LiveUpdateDetector.DetectionResult.toCoreDetection() =
        LiveUpdateDetectorCore.DetectionResult(
            isProgress = isProgress,
            category = category.toCoreCategory(),
            progressPercent = progressPercent,
            progressText = progressText,
            startLabel = startLabel,
            endLabel = endLabel,
            trackerLabel = trackerLabel,
        )

    private fun FocusSemanticCore.Semantic.toFacadeSemantic() = FocusSemantic(
        category = category.toFacadeCategory(),
        style = NotificationStyle.valueOf(style.name),
        progressPercent = progressPercent,
        progressText = progressText,
        startLabel = startLabel,
        endLabel = endLabel,
        trackerLabel = trackerLabel,
        semanticStyle = semanticStyle.toFacadeSemanticStyle(),
        source = Source.valueOf(source.name),
    )

    private fun LiveUpdateDetector.ProgressCategory.toCoreCategory() =
        LiveUpdateDetectorCore.ProgressCategory.valueOf(name)

    private fun LiveUpdateDetectorCore.ProgressCategory.toFacadeCategory() =
        LiveUpdateDetector.ProgressCategory.valueOf(name)

    private fun FocusSemanticCore.SemanticStyle.toFacadeSemanticStyle() = SemanticStyle.valueOf(name)

    private fun SemanticStyle.toCoreSemanticStyle() = FocusSemanticCore.SemanticStyle.valueOf(name)
}
