package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.common.R as CommonR
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.d4viddf.hyperisland_kit.HyperAction
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.ProgressInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.d4viddf.hyperisland_kit.models.TimerInfo
import io.github.magisk317.mipush.common.NotificationStyle
import io.github.magisk317.mipush.common.island.DynamicIslandColorResolver
import io.github.magisk317.mipush.common.island.IslandRendererPolicy
import io.github.magisk317.mipush.common.island.IslandVisualContract
import io.github.magisk317.mipush.common.notification.NotificationProgressTextSupport
import io.github.magisk317.mipush.common.utils.ImgUtils
import co.touchlab.kermit.Logger
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object MiPushIslandPayloadBuilder {
    private const val TAG = "MiPushIslandPayloadBuilder"
    private const val FOCUS_PARAM = "miui.focus.param"
    private const val FOCUS_PICS = "miui.focus.pics"
    private const val FOCUS_ACTIONS = "miui.focus.actions"
    private const val FOCUS_ACTION_PREFIX = "miui.focus.action_"
    private const val PIC_ICON = "miui.focus.pic_mipush_icon"
    private const val PIC_ICON_KEY = "mipush_icon"
    private const val ACTION_OPEN_KEY = "mipush_open"
    private const val BUSINESS = "mipush_framework_push"

    fun canBuild(metaInfo: PushMetaInfo?): Boolean {
        return !metaInfo?.title.isNullOrBlank() || !metaInfo?.description.isNullOrBlank()
    }

    fun buildFocusParam(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
        largeIcon: Bitmap?,
        notificationIcon: Icon? = null,
        options: MiPushIslandOptions = MiPushIslandOptions(),
        channelId: String? = null,
        channelName: String? = null,
        liveUpdateResult: LiveUpdateDetector.DetectionResult? = null,
        styleOverride: NotificationStyle? = null,
    ): String? {
        if (!options.canBuildFocusPayload) return null
        val (title, content) = resolveDisplayText(metaInfo) ?: return null
        val icon = resolveNotificationIcon(context, packageName, notificationIcon, largeIcon)
        val style = styleOverride ?: resolveStyle(metaInfo, packageName, channelId, channelName, liveUpdateResult)
        val dynamicColor = resolveVisualColor(context, packageName, icon, options)
        return createBuilder(
            context,
            title,
            content,
            icon,
            options,
            style,
            liveUpdateResult = liveUpdateResult,
        ).buildJsonParam().injectVisualAppearance(
            highlightColor = dynamicColor,
            outerGlow = options.visualEnabled && options.outerGlowEnabled,
        )
    }

    fun build(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
        largeIcon: Bitmap?,
        notificationId: Int? = null,
        notificationIcon: Icon? = null,
        displayTitle: String? = null,
        displayContent: String? = null,
        contentIntent: PendingIntent? = null,
        actionTitle: CharSequence? = null,
        keepNotificationVisible: Boolean = true,
        options: MiPushIslandOptions = MiPushIslandOptions(),
        channelId: String? = null,
        channelName: String? = null,
        liveUpdateResult: LiveUpdateDetector.DetectionResult? = null,
        styleOverride: NotificationStyle? = null,
    ): Bundle? {
        if (!options.canBuildFocusPayload) return null
        val (title, content) = resolveDisplayText(metaInfo, displayTitle, displayContent) ?: return null
        val appLabel = resolveAppLabel(context, packageName)
        val icon = resolveNotificationIcon(context, packageName, notificationIcon, largeIcon)
        val style = styleOverride ?: resolveStyle(metaInfo, packageName, channelId, channelName, liveUpdateResult)
        val payloadOptions = if (keepNotificationVisible) {
            options.copy(showNotification = true)
        } else {
            options
        }

        val dynamicColor = resolveVisualColor(context, packageName, icon, payloadOptions)
        return Bundle().apply {
            putString(
                FOCUS_PARAM,
                createBuilder(
                    context = context,
                    title = title,
                    content = content,
                    icon = icon,
                    options = payloadOptions,
                    style = style,
                    contentIntent = contentIntent,
                    actionTitle = actionTitle,
                    liveUpdateResult = liveUpdateResult,
                )
                    .buildJsonParam()
                    .injectVisualAppearance(
                        highlightColor = dynamicColor,
                        outerGlow = payloadOptions.visualEnabled && payloadOptions.outerGlowEnabled,
                    )
                    .withNotificationIdentity(packageName, notificationId),
            )
            val owner = IslandRendererPolicy.owner(context, payloadOptions)
            val rendererMode = IslandRendererPolicy.mode(context, payloadOptions)
            putString(IslandVisualContract.OWNER_KEY, owner)
            putInt(IslandVisualContract.VISUAL_VERSION_KEY, IslandVisualContract.VERSION)
            putString(IslandVisualContract.VISUAL_MODE_KEY, rendererMode.wireValue)
            putString(IslandVisualContract.VISUAL_MARKER_KEY, IslandVisualContract.VISUAL_MARKER)
            dynamicColor?.let {
                putString(IslandVisualContract.HIGHLIGHT_COLOR_KEY, it)
                putString(IslandVisualContract.GLOW_COLOR_KEY, it)
                putString(IslandVisualContract.ISLAND_GLOW_COLOR_KEY, it)
                putString(IslandVisualContract.FOCUS_GLOW_COLOR_KEY, it)
            }
            putString("hyperisland_source_pkg", packageName)
            putString("hyperisland_source_label", appLabel)
            putString(PIC_ICON, PIC_ICON)
            putBundle(
                FOCUS_PICS,
                Bundle().apply {
                    putParcelable(PIC_ICON, icon)
                },
            )
            if (contentIntent != null) {
                putFocusAction(icon, actionTitle?.toString()?.takeIf { it.isNotBlank() } ?: title, contentIntent)
            }
        }
    }

    private fun resolveDisplayText(
        metaInfo: PushMetaInfo,
        displayTitle: String? = null,
        displayContent: String? = null,
    ): Pair<String, String>? {
        val title = displayTitle?.takeIf { it.isNotBlank() }
            ?: metaInfo.title?.takeIf { it.isNotBlank() }
        val content = displayContent?.takeIf { it.isNotBlank() }
            ?: metaInfo.description?.takeIf { it.isNotBlank() }
        val primary = title ?: content ?: return null
        return primary to (content ?: primary)
    }

    fun resolveNotificationIcon(context: Context, packageName: String, largeIcon: Bitmap?): Icon {
        return resolveNotificationIcon(context, packageName, null, largeIcon)
    }

    fun resolveNotificationIcon(
        context: Context,
        packageName: String,
        notificationIcon: Icon?,
        largeIcon: Bitmap?,
    ): Icon {
        notificationIcon?.let { return it }
        return largeIcon?.let(Icon::createWithBitmap)
            ?: resolveAppIcon(context, packageName)
            ?: Icon.createWithResource(context, CommonR.drawable.ic_notifications_black_24dp)
    }

    private fun resolveVisualColor(
        context: Context,
        packageName: String,
        icon: Icon,
        options: MiPushIslandOptions,
    ): String? {
        if (!options.visualEnabled || !options.dynamicColor) return null
        return DynamicIslandColorResolver.resolve(
            context = context,
            packageName = packageName,
            notificationIcon = icon,
        )
    }

    internal fun String.injectVisualAppearance(
        highlightColor: String?,
        outerGlow: Boolean,
    ): String {
        if (highlightColor.isNullOrBlank() && !outerGlow) return this
        return runCatching {
            val root = Json.parseToJsonElement(this).jsonObject.toMutableMap()
            val paramV2 = (root["param_v2"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            val paramIsland = (paramV2["param_island"] as? JsonObject)?.toMutableMap() ?: mutableMapOf()
            highlightColor?.takeIf { it.isNotBlank() }?.let {
                paramIsland["highlightColor"] = kotlinx.serialization.json.JsonPrimitive(it)
            }
            if (outerGlow) paramIsland["outEffectSrc"] = kotlinx.serialization.json.JsonPrimitive("outer_glow")
            paramV2["param_island"] = JsonObject(paramIsland)
            root["param_v2"] = JsonObject(paramV2)
            JsonObject(root).toString()
        }.getOrDefault(this)
    }

    /**
     * 解析通知样式：如果 LiveUpdateDetector 检测到进度类通知，优先使用 PROGRESS 样式
     */
    private fun resolveStyle(
        metaInfo: PushMetaInfo,
        packageName: String,
        channelId: String?,
        channelName: String?,
        liveUpdateResult: LiveUpdateDetector.DetectionResult?,
    ): NotificationStyle {
        // LiveUpdate 检测到进度时，强制使用 PROGRESS 样式
        if (liveUpdateResult?.isProgress == true) {
            return NotificationStyle.PROGRESS
        }
        return NotificationClassifier.classify(metaInfo, packageName, channelId, channelName)
    }

    private fun createBuilder(
        context: Context,
        title: String,
        content: String,
        icon: Icon,
        options: MiPushIslandOptions,
        style: NotificationStyle = NotificationStyle.GENERAL,
        contentIntent: PendingIntent? = null,
        actionTitle: CharSequence? = null,
        liveUpdateResult: LiveUpdateDetector.DetectionResult? = null,
    ): HyperIslandNotification {
        val clickAction = contentIntent?.let {
            HyperAction(
                key = ACTION_OPEN_KEY,
                title = actionTitle?.takeIf { it.isNotBlank() } ?: title,
                pendingIntent = it,
                actionIntentType = 1,
            )
        }
        Logger.withTag(TAG).i {
            "createBuilder options: showNotification=${options.showNotification} enableFloat=${options.enableFloat} enabled=${options.enabled} focusNotification=${options.focusNotification} style=$style title=$title"
        }
        val builder = HyperIslandNotification.Builder(
            context = context,
            businessName = BUSINESS,
            ticker = title,
        )
            .addPicture(HyperPicture(PIC_ICON_KEY, icon))
            .setSmallIsland(PIC_ICON_KEY)
            .setIslandConfig(timeout = options.timeoutSecs.coerceAtLeast(1))
            .setIslandFirstFloat(options.firstFloat)
            .setEnableFloat(options.enableFloat)
            .setShowNotification(options.showNotification)
            .setReopen(false)
            .setAodConfig(title = content)

        // 根据分类选择模板
        when (style) {
            NotificationStyle.MESSAGE -> applyChatTemplate(builder, title, content, clickAction)
            NotificationStyle.BANNER -> applyIconTextTemplate(builder, title, content, clickAction)
            NotificationStyle.ALERT -> applyAlertTemplate(builder, title, content, clickAction)
            NotificationStyle.PROMO -> applyHighlightV3Template(builder, title, content, clickAction)
            NotificationStyle.MEDIA -> applyCoverTemplate(builder, title, content, clickAction)
            NotificationStyle.PROGRESS -> applyProgressTemplate(builder, title, content, clickAction, liveUpdateResult)
            NotificationStyle.GENERAL -> applyIconTextTemplate(builder, title, content, clickAction)
        }

        return builder
    }

    /** ChatInfo 模板 - IM/聊天消息 */
    private fun applyChatTemplate(
        builder: HyperIslandNotification,
        title: String,
        content: String,
        clickAction: HyperAction?,
    ) {
        builder.setChatInfo(
            title = title,
            content = content,
            pictureKey = PIC_ICON_KEY,
        )
        builder.setBigIslandInfo(
            left = ImageTextInfoLeft(
                type = 1,
                picInfo = PicInfo(type = 1, pic = PIC_ICON_KEY),
                textInfo = TextInfo(title = title),
            ),
            right = ImageTextInfoRight(
                type = 2,
                textInfo = TextInfo(title = content, narrowFont = true),
            ),
        )
        if (clickAction != null) {
            builder.addHiddenAction(clickAction)
            builder.setHintAction(title, content, clickAction)
        }
    }

    /** IconTextInfo 模板 - 通用/横幅通知，保持头像/应用图标在焦点横幅左侧 */
    private fun applyIconTextTemplate(
        builder: HyperIslandNotification,
        title: String,
        content: String,
        clickAction: HyperAction?,
    ) {
        builder.setIconTextInfo(
            picKey = PIC_ICON_KEY,
            title = title,
            content = content,
        )
        builder.setBigIslandInfo(
            left = ImageTextInfoLeft(
                type = 1,
                picInfo = PicInfo(type = 1, pic = PIC_ICON_KEY),
                textInfo = TextInfo(title = title),
            ),
            right = ImageTextInfoRight(
                type = 2,
                textInfo = TextInfo(title = content, narrowFont = true),
            ),
        )
        if (clickAction != null) {
            builder.addHiddenAction(clickAction)
            builder.setHintAction(title, content, clickAction)
        }
    }

    /** 提醒类：普通提醒用两行图文；明确倒计时才使用 HighlightInfo/倒计时组件。 */
    private fun applyAlertTemplate(
        builder: HyperIslandNotification,
        title: String,
        content: String,
        clickAction: HyperAction?,
    ) {
        val countdownMs = NotificationProgressTextSupport.extractCountdownMillis(title, content)
        if (countdownMs > 0) {
            builder.setHighlightInfo(
                title = title,
                content = content,
                picKey = PIC_ICON_KEY,
            )
            builder.setBigIslandCountdown(countdownMs, PIC_ICON_KEY)
            if (clickAction != null) {
                builder.setHintAction(NotificationProgressTextSupport.resolveAlertHint(title, content) ?: title, null, clickAction)
                builder.addHiddenAction(clickAction)
            }
            return
        }

        builder.setIconTextInfo(
            picKey = PIC_ICON_KEY,
            title = title,
            content = content,
        )
        builder.setBigIslandInfo(
            left = ImageTextInfoLeft(
                type = 1,
                picInfo = PicInfo(type = 1, pic = PIC_ICON_KEY),
                textInfo = TextInfo(title = title, content = content),
            ),
        )
        if (clickAction != null) {
            builder.addHiddenAction(clickAction)
        }
    }

    /** HighlightInfoV3 模板 - 促销/价格 */
    private fun applyHighlightV3Template(
        builder: HyperIslandNotification,
        title: String,
        content: String,
        clickAction: HyperAction?,
    ) {
        builder.setHighlightInfoV3(
            primaryText = title,
            secondaryText = content,
            action = clickAction,
        )
        builder.setBigIslandInfo(
            left = ImageTextInfoLeft(
                type = 1,
                picInfo = PicInfo(type = 1, pic = PIC_ICON_KEY),
                textInfo = TextInfo(title = title),
            ),
            right = ImageTextInfoRight(
                type = 2,
                textInfo = TextInfo(title = content, narrowFont = true),
            ),
        )
    }

    /** CoverInfo 模板 - 媒体/封面 */
    private fun applyCoverTemplate(
        builder: HyperIslandNotification,
        title: String,
        content: String,
        clickAction: HyperAction?,
    ) {
        builder.setCoverInfo(
            picKey = PIC_ICON_KEY,
            title = title,
            content = content,
        )
        builder.setBigIslandInfo(
            left = ImageTextInfoLeft(
                type = 1,
                picInfo = PicInfo(type = 1, pic = PIC_ICON_KEY),
                textInfo = TextInfo(title = title),
            ),
            right = ImageTextInfoRight(
                type = 2,
                textInfo = TextInfo(title = content, narrowFont = true),
            ),
        )
        if (clickAction != null) {
            builder.addHiddenAction(clickAction)
        }
    }

    /** IconTextInfo + Progress 模板 - 进度类通知 */
    private fun applyProgressTemplate(
        builder: HyperIslandNotification,
        title: String,
        content: String,
        clickAction: HyperAction?,
        liveUpdateResult: LiveUpdateDetector.DetectionResult? = null,
    ) {
        builder.setIconTextInfo(
            picKey = PIC_ICON_KEY,
            title = title,
            content = content,
        )

        // 优先使用 LiveUpdateDetector 的检测结果，回退到文本正则提取
        val progress = liveUpdateResult?.progressPercent
            ?: NotificationProgressTextSupport.extractProgressPercent(content)
        val isTracking = isTrackingCategory(liveUpdateResult)

        if (progress in 0..100) {
            builder.setProgressBar(progress = progress)
            builder.setSmallIslandCircularProgress(
                pictureKey = PIC_ICON_KEY,
                progress = progress,
            )
            // 有进度时使用大岛环形进度
            builder.setBigIslandProgressCircle(
                pictureKey = PIC_ICON_KEY,
                title = title,
                progress = progress,
            )
        } else if (isTracking) {
            // 外卖/打车/物流等跟踪类：使用正计时（从现在开始计时）
            builder.setBigIslandCountUp(System.currentTimeMillis(), PIC_ICON_KEY)
            builder.setSmallIsland(PIC_ICON_KEY)
        } else {
            // 进度未知时使用不确定进度条 + 默认岛图标
            builder.setProgressBar(progress = 0)
            builder.setSmallIsland(PIC_ICON_KEY)
            builder.setBigIslandInfo(
                left = ImageTextInfoLeft(
                    type = 1,
                    picInfo = PicInfo(type = 1, pic = PIC_ICON_KEY),
                    textInfo = TextInfo(title = title),
                ),
                right = ImageTextInfoRight(
                    type = 2,
                    textInfo = TextInfo(title = content, narrowFont = true),
                ),
            )
        }

        // 设置 HintInfo 显示分类标签
        val hintLabel = liveUpdateResult?.trackerLabel
            ?: liveUpdateResult?.category?.label
            ?: NotificationProgressTextSupport.resolveProgressHint(title, content)
        if (hintLabel != null) {
            if (clickAction != null) {
                builder.setHintAction(hintLabel, null, clickAction)
            } else {
                builder.setHintInfo(hintLabel)
            }
        }

        if (clickAction != null) {
            builder.addHiddenAction(clickAction)
        }
    }

    /** 判断是否为跟踪类通知（外卖/打车/物流/行程） */
    private fun isTrackingCategory(result: LiveUpdateDetector.DetectionResult?): Boolean {
        if (result?.isProgress != true) return false
        val category = result.category.name
        return category in setOf("DELIVERY", "RIDE_HAILING", "LOGISTICS", "TRAVEL")
    }

    private fun Bundle.putFocusAction(icon: Icon, title: String, contentIntent: PendingIntent) {
        val actionKey = "$FOCUS_ACTION_PREFIX$ACTION_OPEN_KEY"
        val action = Notification.Action.Builder(icon, title, contentIntent).build()
        putBundle(
            FOCUS_ACTIONS,
            Bundle().apply {
                putParcelable(actionKey, action)
            },
        )
        putParcelable(actionKey, action)
    }

    private fun String.withNotificationIdentity(packageName: String, notificationId: Int?): String {
        if (notificationId == null) return this
        return runCatching {
            val identity = "$packageName:$notificationId"
            val root = Json.parseToJsonElement(this).jsonObject.toMutableMap()
            val paramV2Obj = root["param_v2"] as? JsonObject ?: return this
            val paramV2 = paramV2Obj.toMutableMap()
            val currentNotifyId = paramV2["notifyId"]?.jsonPrimitive?.contentOrNull
            if (currentNotifyId.isNullOrBlank()) {
                paramV2["notifyId"] = kotlinx.serialization.json.JsonPrimitive(identity)
            }
            val currentOrderId = paramV2["orderId"]?.jsonPrimitive?.contentOrNull
            if (currentOrderId.isNullOrBlank()) {
                paramV2["orderId"] = kotlinx.serialization.json.JsonPrimitive(identity)
            }
            root["param_v2"] = JsonObject(paramV2)
            JsonObject(root).toString()
        }.getOrDefault(this)
    }

    private fun resolveAppIcon(context: Context, packageName: String): Icon? {
        return runCatching {
            val drawable = context.packageManager.getApplicationIcon(packageName)
            Icon.createWithBitmap(ImgUtils.drawableToBitmap(drawable))
        }.getOrNull()
    }

    private fun resolveAppLabel(context: Context, packageName: String): String {
        return runCatching {
            val appInfo = context.packageManager.getApplicationInfo(packageName, 0)
            context.packageManager.getApplicationLabel(appInfo).toString()
        }.getOrDefault(packageName)
    }
}
