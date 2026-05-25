package io.github.magisk317.mipush.notification

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Icon
import android.os.Bundle
import com.xiaomi.xmpush.thrift.PushMetaInfo
import io.github.d4viddf.hyperisland_kit.HyperIslandNotification
import io.github.d4viddf.hyperisland_kit.HyperPicture
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoLeft
import io.github.d4viddf.hyperisland_kit.models.ImageTextInfoRight
import io.github.d4viddf.hyperisland_kit.models.PicInfo
import io.github.d4viddf.hyperisland_kit.models.TextInfo
import io.github.magisk317.mipush.common.utils.ImgUtils
import com.xiaomi.xmsf.R

internal object MiPushIslandPayloadBuilder {
    private const val FOCUS_PARAM = "miui.focus.param"
    private const val FOCUS_PICS = "miui.focus.pics"
    private const val PIC_ICON = "miui.focus.pic_mipush_icon"
    private const val PIC_ICON_KEY = "mipush_icon"
    private const val BUSINESS = "mipush_framework_push"
    private const val DEFAULT_TIMEOUT_SECS = 5

    fun canBuild(metaInfo: PushMetaInfo?): Boolean {
        return !metaInfo?.title.isNullOrBlank()
    }

    fun buildFocusParam(
        context: Context,
        metaInfo: PushMetaInfo,
        options: MiPushIslandOptions = MiPushIslandOptions(),
    ): String? {
        if (!options.canBuildFocusPayload) return null
        val title = metaInfo.title?.takeIf { it.isNotBlank() } ?: return null
        val content = metaInfo.description?.takeIf { it.isNotBlank() } ?: title
        val icon = Icon.createWithResource(context, R.drawable.ic_notifications_black_24dp)
        return createBuilder(context, title, content, icon, options).buildJsonParam()
    }

    fun build(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
        largeIcon: Bitmap?,
        options: MiPushIslandOptions = MiPushIslandOptions(),
    ): Bundle? {
        if (!options.canBuildFocusPayload) return null
        if (!canBuild(metaInfo)) return null
        val title = metaInfo.title?.takeIf { it.isNotBlank() } ?: return null
        val content = metaInfo.description?.takeIf { it.isNotBlank() } ?: title
        val appLabel = resolveAppLabel(context, packageName)
        val icon = largeIcon?.let(Icon::createWithBitmap)
            ?: resolveAppIcon(context, packageName)
            ?: Icon.createWithResource(context, R.drawable.ic_notifications_black_24dp)

        return Bundle().apply {
            putString(FOCUS_PARAM, createBuilder(context, title, content, icon, options).buildJsonParam())
            putString("hyperisland_source_pkg", packageName)
            putString("hyperisland_source_label", appLabel)
            putString(PIC_ICON, PIC_ICON)
            putBundle(
                FOCUS_PICS,
                Bundle().apply {
                    putParcelable(PIC_ICON, icon)
                },
            )
        }
    }

    private fun createBuilder(
        context: Context,
        title: String,
        content: String,
        icon: Icon,
        options: MiPushIslandOptions,
    ): HyperIslandNotification {
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
