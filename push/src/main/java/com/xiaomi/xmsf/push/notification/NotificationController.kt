@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf.push.notification

import android.annotation.TargetApi
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import com.elvishew.xlog.XLog
import com.nihility.Global
import com.nihility.XMPushUtils
import com.nihility.notification.NotificationManagerEx
import com.xiaomi.push.service.MyMIPushNotificationHelper
import com.xiaomi.push.service.MyNotificationIconHelper
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.push.utils.Configurations
import com.xiaomi.xmsf.push.utils.IconConfigurations
import com.xiaomi.xmsf.utils.ColorUtil
import top.trumeet.common.utils.CustomConfiguration
import top.trumeet.common.utils.ImgUtils
import top.trumeet.mipushframework.main.AdvancedSettingsPage

object NotificationController {
    private val logger = XLog.tag("NotificationController").build()
    private const val NOTIFICATION_LARGE_ICON = "mipush_notification"
    private const val NOTIFICATION_SMALL_ICON = "mipush_small_notification"
    private const val KIB = 1024
    const val CHANNEL_WARN = "warn"

    @JvmStatic
    fun getNotificationManagerEx(): NotificationManagerEx = NotificationManagerEx

    @TargetApi(Build.VERSION_CODES.N)
    private fun updateSummaryNotification(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
        groupId: String?
    ) {
        if (groupId == null) {
            return
        }
        if (!needGroupOfNotifications(packageName, groupId)) {
            getNotificationManagerEx().cancel(packageName, null, groupId.hashCode())
            return
        }
        val builder = NotificationCompat.Builder(context, getExistsChannelId(context, metaInfo, packageName))
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        builder.setCategory(Notification.CATEGORY_EVENT).setGroupSummary(true).setGroup(groupId)
        notify(context, groupId.hashCode(), packageName, builder, metaInfo)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun needGroupOfNotifications(packageName: String, groupId: String): Boolean =
        getNotificationCountOfGroup(packageName, groupId) > 1

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getNotificationCountOfGroup(packageName: String, groupId: String): Int {
        val activeNotifications = getNotificationManagerEx().getActiveNotifications(packageName) ?: return 0
        var notificationCntInGroup = 0
        for (statusBarNotification in activeNotifications) {
            val safeNotification = statusBarNotification ?: continue
            if (groupId == safeNotification.notification.group) {
                notificationCntInGroup++
            }
        }
        return notificationCntInGroup
    }

    @JvmStatic
    fun publish(
        context: Context,
        metaInfo: PushMetaInfo,
        notificationId: Int,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder
    ) {
        val channelId = getExistsChannelId(context, metaInfo, packageName)
        notificationBuilder.setChannelId(channelId)
        notificationBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        notificationBuilder.setDefaults(Notification.DEFAULT_ALL)
        notificationBuilder.priority = Notification.PRIORITY_HIGH

        val notification = notify(context, notificationId, packageName, notificationBuilder, metaInfo)
        updateSummaryNotification(context, metaInfo, packageName, notification.group)
    }

    @JvmStatic
    fun getExistsChannelId(context: Context, metaInfo: PushMetaInfo, packageName: String): String {
        val custom = XMPushUtils.getConfiguration(metaInfo)
        var channelId = custom.borrowChannelId(null)
        if (TextUtils.isEmpty(channelId) ||
            getNotificationManagerEx().getNotificationChannel(packageName, channelId) == null
        ) {
            NotificationChannelManager.registerChannelIfNeeded(context, metaInfo, packageName)
            channelId = NotificationChannelManager.getChannelId(metaInfo, packageName)
        }
        return channelId ?: ""
    }

    private fun notify(
        context: Context,
        notificationId: Int,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder,
        metaInfo: PushMetaInfo
    ): Notification {
        val extras = Bundle()
        extras.putString("target_package", packageName)
        notificationBuilder.addExtras(extras)
        processIcon(context, packageName, notificationBuilder)

        val configuration = XMPushUtils.getConfiguration(metaInfo)
        val iconUri = configuration.notificationLargeIconUri(null)
        val largeIcon = getLargeIcon(context, metaInfo, iconUri)
        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon)
        }

        val subText = configuration.subText(null)
        buildExtraSubText(context, packageName, notificationBuilder, subText)

        val focusParam = configuration.focusParam(null)
        if (focusParam != null) {
            val focusBundle = Bundle()
            focusBundle.putString("miui.focus.param", focusParam)
            val picsBundle = Bundle()
            for (key in configuration.keys()) {
                if (key.startsWith("miui.focus.pic_")) {
                    val url = configuration.get(key, null)
                    focusBundle.putString(key, url)
                    picsBundle.putParcelable(key, getBitmapFromUri(context, iconUri, 200 * KIB))
                }
            }
            if (!picsBundle.isEmpty) {
                focusBundle.putBundle("miui.focus.pics", picsBundle)
            }
            notificationBuilder.addExtras(focusBundle)
        }

        notificationBuilder.setAutoCancel(true)
        val notification = notificationBuilder.build()
        getNotificationManagerEx().notify(
            packageName,
            MyMIPushNotificationHelper.getNotificationTag(packageName),
            notificationId,
            notification
        )
        return notification
    }

    @JvmStatic
    fun getLargeIcon(context: Context, metaInfo: PushMetaInfo, iconUri: String?): Bitmap? {
        var largeIcon = if (iconUri == null) null else getBitmapFromUri(context, iconUri, 200 * KIB)
        if (largeIcon != null) {
            largeIcon = roundLargeIconIfConfigured(metaInfo, largeIcon)
        }
        return largeIcon
    }

    @JvmStatic
    fun roundLargeIconIfConfigured(metaInfo: PushMetaInfo, largeIcon: Bitmap): Bitmap {
        var result = largeIcon
        val custom = XMPushUtils.getConfiguration(metaInfo)
        if (custom.roundLargeIcon(false)) {
            result = ImgUtils.trimImgToCircle(result, Color.TRANSPARENT)
        }
        return result
    }

    @JvmStatic
    fun getBitmapFromUri(context: Context, iconUri: String?, maxDownloadBytes: Int): Bitmap? {
        var bitmap: Bitmap? = null
        if (iconUri != null) {
            if (iconUri.startsWith("http")) {
                val result = MyNotificationIconHelper.getIconFromUrl(context, iconUri, maxDownloadBytes)
                if (result != null) {
                    bitmap = result.bitmap
                }
            } else {
                bitmap = MyNotificationIconHelper.getIconFromUri(context, iconUri)
            }
        }
        return bitmap
    }

    @JvmStatic
    fun cancel(
        context: Context,
        container: XmPushActionContainer,
        notificationId: Int,
        notificationGroup: String?,
        clearGroup: Boolean
    ) {
        getNotificationManagerEx().cancel(
            container.packageName,
            MyMIPushNotificationHelper.getNotificationTag(container),
            notificationId
        )
        if (clearGroup) {
            getNotificationManagerEx().cancel(
                container.packageName,
                MyMIPushNotificationHelper.getNotificationTag(container),
                notificationGroup?.hashCode() ?: 0
            )
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && notificationGroup != null) {
            val copy = container.deepCopy()
            try {
                Configurations.getInstance().handle(container.packageName, copy)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            val copyMeta = copy.metaInfo ?: return
            updateSummaryNotification(context, copyMeta, container.packageName, notificationGroup)
        }
    }

    @JvmStatic
    fun getIconColor(ctx: Context, pkg: String): Int {
        return Global.IconCache().getAppColor(
            ctx,
            pkg,
            object : top.trumeet.common.cache.IconCache.Converter<Bitmap, Int> {
                override fun convert(ctx: Context, b: Bitmap): Int {
                    val color = ColorUtil.getIconColor(b)
                    if (color != Notification.COLOR_DEFAULT) {
                        val hsl = FloatArray(3)
                        ColorUtils.colorToHSL(color, hsl)
                        hsl[1] = 0.94f
                        hsl[2] = minOf(hsl[2] * 0.6f, 0.31f)
                        return ColorUtils.HSLToColor(hsl)
                    }
                    return Notification.COLOR_DEFAULT
                }
            }
        )
    }

    @JvmStatic
    fun processIcon(context: Context, packageName: String, notificationBuilder: NotificationCompat.Builder) {
        notificationBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp)
        val pkgContext = try {
            context.createPackageContext(
                packageName,
                Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
            )
        } catch (_: PackageManager.NameNotFoundException) {
            return
        }
        val largeIconId = getIconId(context, packageName, NOTIFICATION_LARGE_ICON)
        val smallIconId = getIconId(context, packageName, NOTIFICATION_SMALL_ICON)
        if (largeIconId > 0) {
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(pkgContext.resources, largeIconId))
        }
        notificationBuilder.color = getIconColor(context, packageName)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val iconConfig = Global.IconConfigurations().get(packageName)
            if (iconConfig != null && iconConfig.isEnabled == true && iconConfig.isEnabledAll == true) {
                val iconBitmap = iconConfig.bitmap()
                if (iconBitmap != null) {
                    notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(iconBitmap))
                    notificationBuilder.color = iconConfig.color()
                    return
                }
            }
            if (smallIconId > 0) {
                notificationBuilder.setSmallIcon(IconCompat.createWithResource(pkgContext, smallIconId))
                return
            }
            if (largeIconId > 0) {
                notificationBuilder.setSmallIcon(IconCompat.createWithResource(pkgContext, largeIconId))
                return
            }
            val iconBitmap = iconConfig?.bitmap()
            if (iconBitmap != null && iconConfig.isEnabled == true) {
                notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(iconBitmap))
                notificationBuilder.color = iconConfig.color()
                return
            }
            val iconCache = Global.IconCache().getIconCache(
                context,
                packageName,
                object : top.trumeet.common.cache.IconCache.Converter<Bitmap, IconCompat> {
                    override fun convert(ctx: Context, b: Bitmap): IconCompat = IconCompat.createWithBitmap(b)
                }
            )
            if (iconCache != null) {
                notificationBuilder.setSmallIcon(iconCache)
            }
        }
    }

    @JvmStatic
    fun buildExtraSubText(
        context: Context,
        packageName: String,
        localBuilder: NotificationCompat.Builder,
        text: CharSequence?
    ) {
        var localText = text
        if ("".contentEquals(localText)) {
            localBuilder.setSubText(null)
            return
        }
        if (localText == null) {
            localText = Global.ApplicationNameCache().getAppName(context, packageName)
        }
        val color = localBuilder.color
        if (color == Notification.COLOR_DEFAULT) {
            localBuilder.setSubText(localText)
            return
        }
        val subText = ColorUtil.createColorSubtext(localText ?: "", color)
        localBuilder.setSubText(subText)
    }

    private fun getIconId(context: Context, packageName: String, resourceName: String): Int =
        context.resources.getIdentifier(resourceName, "drawable", packageName)

    @JvmStatic
    fun test(context: Context, packageName: String, title: String, description: String) {
        NotificationChannelManager.registerChannelIfNeeded(context, PushMetaInfo(), packageName)
        val id = (System.currentTimeMillis() / 1000L).toInt()
        val localBuilder = NotificationCompat.Builder(context)
        val style = NotificationCompat.BigTextStyle()
        style.bigText(description)
        style.setBigContentTitle(title)
        style.setSummaryText(description)
        localBuilder.setStyle(style)
        localBuilder.setWhen(System.currentTimeMillis())
        localBuilder.setShowWhen(true)

        val notifyIntent = Intent(context, AdvancedSettingsPage::class.java)
        notifyIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        val notifyPendingIntent = PendingIntent.getActivity(
            context,
            0,
            notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        localBuilder.setContentIntent(notifyPendingIntent)
        publish(context, PushMetaInfo(), id, packageName, localBuilder)
    }
}
