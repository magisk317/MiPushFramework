package io.github.magisk317.mipush.notification

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import io.github.magisk317.mipush.common.R as CommonR
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.utils.ColorUtil

/** Keeps package/icon resolution out of the notification publication coordinator. */
internal object NotificationIconRenderingSupport {
    private const val NOTIFICATION_LARGE_ICON = "mipush_notification"
    private const val NOTIFICATION_SMALL_ICON = "mipush_small_notification"

    fun getIconColor(context: Context, packageName: String): Int = Global.iconCache().getAppColor(
        context,
        packageName,
        object : io.github.magisk317.mipush.common.cache.IconCache.Converter<Bitmap, Int> {
            override fun convert(ctx: Context, b: Bitmap): Int {
                val color = ColorUtil.getIconColor(b)
                if (color == Notification.COLOR_DEFAULT) return Notification.COLOR_DEFAULT
                val hsl = FloatArray(3)
                ColorUtils.colorToHSL(color, hsl)
                hsl[1] = 0.94f
                hsl[2] = minOf(hsl[2] * 0.6f, 0.31f)
                return ColorUtils.HSLToColor(hsl)
            }
        },
    )

    fun processIcon(
        context: Context,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder,
    ): Int {
        var color = getIconColor(context, packageName)
        notificationBuilder.setSmallIcon(CommonR.drawable.ic_notifications_black_24dp)
        val packageContext = XMPushUtils.getPackageContext(context, packageName, Context.CONTEXT_IGNORE_SECURITY)
        if (packageContext === context) {
            setAppIconSmallIcon(context, packageName, notificationBuilder)
            return color
        }
        val largeIconId = getIconId(context, packageName, NOTIFICATION_LARGE_ICON)
        val smallIconId = getIconId(context, packageName, NOTIFICATION_SMALL_ICON)
        if (largeIconId > 0) {
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(packageContext.resources, largeIconId))
        }
        notificationBuilder.setColor(color)

        val iconConfig = Global.iconConfigurations().get(packageName)
        if (iconConfig != null && iconConfig.isEnabled == true && iconConfig.isEnabledAll == true) {
            iconConfig.bitmap()?.let {
                notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(it))
                color = iconConfig.color()
                notificationBuilder.setColor(color)
                return color
            }
        }
        if (smallIconId > 0) {
            notificationBuilder.setSmallIcon(IconCompat.createWithResource(packageContext, smallIconId))
            return color
        }
        if (largeIconId > 0) {
            notificationBuilder.setSmallIcon(IconCompat.createWithResource(packageContext, largeIconId))
            return color
        }
        val configuredBitmap = iconConfig?.bitmap()
        if (configuredBitmap != null && iconConfig.isEnabled == true) {
            notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(configuredBitmap))
            color = iconConfig.color()
            notificationBuilder.setColor(color)
            return color
        }
        Global.iconCache().getIconCache(
            context,
            packageName,
            object : io.github.magisk317.mipush.common.cache.IconCache.Converter<Bitmap, IconCompat> {
                override fun convert(ctx: Context, b: Bitmap): IconCompat = IconCompat.createWithBitmap(b)
            },
        )?.let {
            notificationBuilder.setSmallIcon(it)
            return color
        }
        setAppIconSmallIcon(context, packageName, notificationBuilder)
        return color
    }

    private fun getIconId(context: Context, packageName: String, resourceName: String): Int =
        context.resources.getIdentifier(resourceName, "drawable", packageName)

    private fun setAppIconSmallIcon(
        context: Context,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder,
    ): Boolean {
        val bitmap = Global.iconCache().getRawIconBitmap(context, packageName) ?: return false
        notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(bitmap))
        return true
    }
}
