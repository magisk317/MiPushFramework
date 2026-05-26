package io.github.magisk317.mipush.notification

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
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.magisk317.mipush.common.utils.ImgUtils
import com.xiaomi.xmsf.R
import org.json.JSONObject

internal object MiPushIslandPayloadBuilder {
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
    ): String? {
        if (!options.canBuildFocusPayload) return null
        val (title, content) = resolveDisplayText(metaInfo) ?: return null
        val icon = resolveNotificationIcon(context, packageName, notificationIcon, largeIcon)
        return createBuilder(context, title, content, icon, options).buildJsonParam()
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
    ): Bundle? {
        if (!options.canBuildFocusPayload) return null
        val (title, content) = resolveDisplayText(metaInfo, displayTitle, displayContent) ?: return null
        val appLabel = resolveAppLabel(context, packageName)
        val icon = resolveNotificationIcon(context, packageName, notificationIcon, largeIcon)
        val payloadOptions = if (keepNotificationVisible) {
            options.copy(showNotification = true)
        } else {
            options
        }

        return Bundle().apply {
            putString(
                FOCUS_PARAM,
                createBuilder(
                    context = context,
                    title = title,
                    content = content,
                    icon = icon,
                    options = payloadOptions,
                    contentIntent = contentIntent,
                    actionTitle = actionTitle,
                )
                    .buildJsonParam()
                    .withNotificationIdentity(packageName, notificationId),
            )
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
            ?: Icon.createWithResource(context, R.drawable.ic_notifications_black_24dp)
    }

    private fun createBuilder(
        context: Context,
        title: String,
        content: String,
        icon: Icon,
        options: MiPushIslandOptions,
        contentIntent: PendingIntent? = null,
        actionTitle: CharSequence? = null,
    ): HyperIslandNotification {
        val clickAction = contentIntent?.let {
            HyperAction(
                key = ACTION_OPEN_KEY,
                title = actionTitle?.takeIf { it.isNotBlank() } ?: title,
                pendingIntent = it,
                actionIntentType = 1,
            )
        }
        return HyperIslandNotification.Builder(
            context = context,
            businessName = BUSINESS,
            ticker = title,
        )
            .addPicture(HyperPicture(PIC_ICON_KEY, icon))
            .setIconTextInfo(PIC_ICON_KEY, title, content)
            .setSmallIsland(PIC_ICON_KEY)
            .setBigIslandInfo(
                left = ImageTextInfoLeft(
                    type = 1,
                    picInfo = PicInfo(type = 1, pic = PIC_ICON_KEY),
                    textInfo = TextInfo(title = title),
                ),
                right = ImageTextInfoRight(
                    type = 2,
                    textInfo = TextInfo(
                        title = content,
                        narrowFont = true,
                    ),
                ),
            )
            .setIslandConfig(timeout = options.timeoutSecs.coerceAtLeast(1))
            .setIslandFirstFloat(options.firstFloat)
            .setEnableFloat(options.enableFloat)
            .setShowNotification(options.showNotification)
            .setReopen(false)
            .setAodConfig(title = content)
            .apply {
                if (clickAction != null) {
                    addHiddenAction(clickAction)
                    setHintAction(title, content, clickAction)
                }
            }
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
            val root = JSONObject(this)
            val paramV2 = root.optJSONObject("param_v2") ?: return this
            if (paramV2.optString("notifyId").isBlank()) {
                paramV2.put("notifyId", identity)
            }
            if (paramV2.optString("orderId").isBlank()) {
                paramV2.put("orderId", identity)
            }
            root.toString()
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
