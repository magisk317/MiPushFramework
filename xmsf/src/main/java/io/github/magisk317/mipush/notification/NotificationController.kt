package io.github.magisk317.mipush.notification

import android.annotation.TargetApi
import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.text.TextUtils
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.drawable.IconCompat
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationStyleSupport
import com.xiaomi.push.service.MyNotificationIconHelper
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.R
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.IconConfigurations
import io.github.magisk317.mipush.utils.ColorUtil
import io.github.magisk317.mipush.common.utils.CustomConfiguration
import io.github.magisk317.mipush.common.utils.ImgUtils
import io.github.magisk317.mipush.feature.main.MainActivity
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints

object NotificationController {
    private const val TAG = "NotificationController"
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = TAG)
    }
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
        if (!VoipNotificationHelper.isVoipNotification(metaInfo)) {
            notificationBuilder.priority = NotificationCompat.PRIORITY_HIGH
        }

        val description = metaInfo.description
        if (SweetTagHandler.containsFtTag(description)) {
            notificationBuilder.setContentText(SweetTagHandler.renderFtHtmlIfNeeded(description))
        }

        val notification = notify(context, notificationId, packageName, notificationBuilder, metaInfo) ?: return
        updateSummaryNotification(context, metaInfo, packageName, notification.group)
    }

    @JvmStatic
    fun getExistsChannelId(context: Context, metaInfo: PushMetaInfo, packageName: String): String {
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val preferredBorrowed = custom.borrowChannelId(null)
        val borrowedChannel = getNotificationManagerEx().findPreferredTargetChannel(packageName, preferredBorrowed)
        if (borrowedChannel != null) {
            if (borrowedChannel.id != preferredBorrowed) {
                logger.d("getExistsChannelId() auto-borrow channel pkg=$packageName channel=${borrowedChannel.id}")
            }
            return borrowedChannel.id
        }
        val fallbackChannelId = NotificationChannelManager.getChannelId(metaInfo, packageName)
        if (getNotificationManagerEx().supportsTargetChannelProvisioning(packageName)) {
            NotificationChannelManager.registerChannelIfNeeded(context, metaInfo, packageName)
            return fallbackChannelId
        }
        NotificationChannelManager.registerChannelIfNeeded(context, metaInfo, packageName)
        logger.d("getExistsChannelId() fallback to local channel pkg=$packageName channel=$fallbackChannelId")
        return fallbackChannelId
    }

    private fun notify(
        context: Context,
        notificationId: Int,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder,
        metaInfo: PushMetaInfo
    ): Notification? {
        val extras = Bundle()
        extras.putString("target_package", packageName)
        extras.putString("miui.targetPkg", packageName)
        notificationBuilder.addExtras(extras)
        val color = processIcon(context, packageName, notificationBuilder)

        val configuration = XMPushUtils.getConfiguration(metaInfo)
        val iconUri = configuration.notificationLargeIconUri(null)
        val largeIcon = getLargeIcon(context, metaInfo, iconUri)
        if (largeIcon != null) {
            notificationBuilder.setLargeIcon(largeIcon)
        }

        val subText = configuration.subText(null)
        buildExtraSubText(context, packageName, notificationBuilder, subText, color)

        val focusBundle = buildFocusBundle(configuration) { url ->
            getBitmapFromUri(context, url, 200 * KIB)
        }
        if (focusBundle != null) {
            notificationBuilder.addExtras(focusBundle)
            notificationBuilder.priority = NotificationCompat.PRIORITY_HIGH
        }

        NotificationSortFilter.attachDeleteIntentIfNeeded(context, notificationBuilder, packageName, metaInfo, notificationId)
        if (!VoipNotificationHelper.isVoipNotification(metaInfo)) {
            notificationBuilder.setAutoCancel(true)
        }
        val notification = notificationBuilder.build()
        val channel = getNotificationManagerEx().getNotificationChannel(packageName, notification.channelId)
        if (!NotificationContentSupport.hasMeaningfulVisibleText(context, packageName, notification, channel)) {
            logger.d("drop contentless notification pkg=$packageName id=$notificationId channel=${notification.channelId}")
            return null
        }
        val tag = MyMIPushNotificationHelper.getNotificationTag(packageName)
        getNotificationManagerEx().notify(packageName, tag, notificationId, notification)
        if (focusBundle != null) {
            val key = "0|$packageName|$notificationId|$tag|0"
            FocusNotificationRegistry.register(context, key)
        }
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
                bitmap = result.bitmap
            } else {
                bitmap = MyNotificationIconHelper.getIconFromUri(context, iconUri)
            }
        }
        return bitmap
    }

    internal fun collectFocusPicUris(configuration: CustomConfiguration): Map<String, String> {
        return configuration.keys()
            .filter { it.startsWith("miui.focus.pic_") }
            .mapNotNull { key ->
                val uri = configuration.get(key, null)
                if (uri.isNullOrBlank()) null else key to uri
            }
            .toMap()
    }

    internal fun buildFocusBundle(
        configuration: CustomConfiguration,
        bitmapLoader: (String) -> Bitmap?
    ): Bundle? {
        val focusParam = configuration.focusParam(null) ?: return null
        val focusBundle = Bundle()
        focusBundle.putString("miui.focus.param", focusParam)
        val picsBundle = Bundle()
        for ((key, url) in collectFocusPicUris(configuration)) {
            focusBundle.putString(key, url)
            val bitmap = bitmapLoader(url)
            if (bitmap != null) {
                picsBundle.putParcelable(key, Icon.createWithBitmap(bitmap))
            }
        }
        if (!picsBundle.isEmpty) {
            focusBundle.putBundle("miui.focus.pics", picsBundle)
        }
        return focusBundle
    }

    @JvmStatic
    fun cancel(
        context: Context,
        container: XmPushActionContainer,
        notificationId: Int,
        notificationGroup: String?,
        clearGroup: Boolean
    ) {
        MyMIPushNotificationStyleSupport.clearConversationHistory(container.packageName, notificationId)
        val tag = MyMIPushNotificationHelper.getNotificationTag(container)
        getNotificationManagerEx().cancel(container.packageName, tag, notificationId)
        FocusNotificationRegistry.unregister(context, "0|${container.packageName}|$notificationId|$tag|0")
        if (clearGroup) {
            getNotificationManagerEx().cancel(
                container.packageName,
                MyMIPushNotificationHelper.getNotificationTag(container),
                notificationGroup?.hashCode() ?: 0
            )
            return
        }
        if (notificationGroup != null) {
            val copy = container.deepCopy()
            try {
                Configurations.getInstance().handle(container.packageName, copy)
            } catch (e: Throwable) {
                io.github.aakira.napier.Napier.e("NotificationController: handle config failed for ${container.packageName}", e, tag = "NotificationController")
            }
            val copyMeta = copy.metaInfo ?: return
            updateSummaryNotification(context, copyMeta, container.packageName, notificationGroup)
        }
    }

    @JvmStatic
    fun getIconColor(ctx: Context, pkg: String): Int {
        return Global.iconCache().getAppColor(
            ctx,
            pkg,
            object : io.github.magisk317.mipush.common.cache.IconCache.Converter<Bitmap, Int> {
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
    fun processIcon(context: Context, packageName: String, notificationBuilder: NotificationCompat.Builder): Int {
        var color = getIconColor(context, packageName)
        notificationBuilder.setSmallIcon(R.drawable.ic_notifications_black_24dp)
        val pkgContext = XMPushUtils.getPackageContext(
            context,
            packageName,
            Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY
        )
        if (pkgContext === context) {
            // Means it failed or not hooked
            return color
        }
        val largeIconId = getIconId(context, packageName, NOTIFICATION_LARGE_ICON)
        val smallIconId = getIconId(context, packageName, NOTIFICATION_SMALL_ICON)
        if (largeIconId > 0) {
            notificationBuilder.setLargeIcon(BitmapFactory.decodeResource(pkgContext.resources, largeIconId))
        }
        notificationBuilder.setColor(color)

        run {
            val iconConfig = Global.iconConfigurations().get(packageName)
            if (iconConfig != null && iconConfig.isEnabled == true && iconConfig.isEnabledAll == true) {
                val iconBitmap = iconConfig.bitmap()
                if (iconBitmap != null) {
                    notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(iconBitmap))
                    color = iconConfig.color()
                    notificationBuilder.setColor(color)
                    return color
                }
            }
            if (smallIconId > 0) {
                notificationBuilder.setSmallIcon(IconCompat.createWithResource(pkgContext, smallIconId))
                return color
            }
            if (largeIconId > 0) {
                notificationBuilder.setSmallIcon(IconCompat.createWithResource(pkgContext, largeIconId))
                return color
            }
            val iconBitmap = iconConfig?.bitmap()
            if (iconBitmap != null && iconConfig.isEnabled == true) {
                notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(iconBitmap))
                color = iconConfig.color()
                notificationBuilder.setColor(color)
                return color
            }
            val iconCache = Global.iconCache().getIconCache(
                context,
                packageName,
                object : io.github.magisk317.mipush.common.cache.IconCache.Converter<Bitmap, IconCompat> {
                    override fun convert(ctx: Context, b: Bitmap): IconCompat = IconCompat.createWithBitmap(b)
                }
            )
            if (iconCache != null) {
                notificationBuilder.setSmallIcon(iconCache)
            }
        }
        return color
    }

    @JvmStatic
    fun buildExtraSubText(
        context: Context,
        packageName: String,
        localBuilder: NotificationCompat.Builder,
        text: CharSequence?,
        color: Int
    ) {
        var localText = text
        if ("".contentEquals(localText)) {
            localBuilder.setSubText(null)
            return
        }
        if (localText == null) {
            localText = Global.applicationNameCache().getAppName(context, packageName)
        }
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
        testMock(context, io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.BIG_TEXT, packageName)
    }

    @JvmStatic
    fun testMock(
        context: Context,
        kind: io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind,
        packageName: String
    ) {
        val metaInfo = PushMetaInfo()
        val extra = HashMap<String, String>()
        val title = context.getString(R.string.debug_test_title)
        val description = context.getString(R.string.debug_test_content) + " " + java.util.Date()
        metaInfo.title = title
        metaInfo.description = description

        when (kind) {
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_BASIC -> {
                extra["miui.focus.param"] = """{"updatable":true,"reopen":"close"}"""
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_WITH_PIC -> {
                extra["miui.focus.param"] = """{"updatable":true,"reopen":"close"}"""
                extra["miui.focus.pic_main"] = "https://cdn.cnbj1.fds.api.mi-img.com/mipush/focus_demo.png"
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.VOIP_INCOMING -> {
                extra["notification_style_type"] = "6"
                extra["msg_busi_type"] = "voip"
                extra["voip_type"] = "1"
                extra["sequence"] = System.currentTimeMillis().toString()
            }
            else -> {}
        }
        metaInfo.extra = extra

        NotificationChannelManager.registerChannelIfNeeded(context, metaInfo, packageName)
        val id = (System.currentTimeMillis() / 1000L).toInt()
        val channelId = NotificationChannelManager.getChannelId(metaInfo, packageName)
        val builder = NotificationCompat.Builder(context, channelId)
        builder.setWhen(System.currentTimeMillis())
        builder.setShowWhen(true)

        val notifyIntent = LegacyUiEntryPoints.mainActivityIntent(
            context = context,
            startTab = MainActivity.START_TAB_SETTINGS,
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val notifyPendingIntent = PendingIntent.getActivity(
            context, 0, notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(notifyPendingIntent)

        when (kind) {
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.PLAIN -> {
                builder.setContentTitle(title)
                builder.setContentText(description)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.BIG_TEXT -> {
                val style = NotificationCompat.BigTextStyle()
                style.bigText(description)
                style.setBigContentTitle(title)
                style.setSummaryText(description)
                builder.setStyle(style)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.BIG_PICTURE -> {
                val style = NotificationCompat.BigPictureStyle()
                style.setBigContentTitle(title)
                style.setSummaryText(description)
                builder.setStyle(style)
                builder.setContentTitle(title)
                builder.setContentText(description)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.INBOX -> {
                val style = NotificationCompat.InboxStyle()
                style.setBigContentTitle(title)
                style.addLine("Line 1: $description")
                style.addLine("Line 2: Another message")
                style.addLine("Line 3: Third message")
                style.setSummaryText("+3 messages")
                builder.setStyle(style)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.MESSAGING -> {
                val person = androidx.core.app.Person.Builder().setName("Test User").build()
                val style = NotificationCompat.MessagingStyle(person)
                style.setConversationTitle("Test Conversation")
                style.addMessage(description, System.currentTimeMillis(), person)
                style.addMessage("Reply message", System.currentTimeMillis() + 1000, person)
                builder.setStyle(style)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.MEDIA -> {
                builder.setContentTitle(title)
                builder.setContentText("Now Playing - $description")
                builder.setOngoing(true)
                builder.addAction(R.drawable.ic_notifications_black_24dp, "Prev", notifyPendingIntent)
                builder.addAction(R.drawable.ic_notifications_black_24dp, "Pause", notifyPendingIntent)
                builder.addAction(R.drawable.ic_notifications_black_24dp, "Next", notifyPendingIntent)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.PROGRESS -> {
                builder.setContentTitle(title)
                builder.setContentText("Downloading...")
                builder.setProgress(100, 65, false)
                builder.setOngoing(true)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.HEADS_UP -> {
                builder.setContentTitle(title)
                builder.setContentText(description)
                builder.priority = NotificationCompat.PRIORITY_HIGH
                builder.setCategory(Notification.CATEGORY_ALARM)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_BASIC,
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_WITH_PIC -> {
                builder.setContentTitle(title)
                builder.setContentText(description)
                builder.priority = NotificationCompat.PRIORITY_HIGH
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.VOIP_INCOMING -> {
                builder.setContentTitle("Incoming Call")
                builder.setContentText(description)
                builder.priority = NotificationCompat.PRIORITY_MAX
                builder.setCategory(Notification.CATEGORY_CALL)
                builder.setOngoing(true)
                builder.setFullScreenIntent(notifyPendingIntent, true)
            }
        }
        publish(context, metaInfo, id, packageName, builder)
    }
}
