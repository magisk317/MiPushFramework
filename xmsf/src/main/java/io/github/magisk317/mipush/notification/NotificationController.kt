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

        // Framework-side Live Update detection and styling
        val liveUpdateResult = LiveUpdateDetector.detect(context, metaInfo, packageName)
        if (liveUpdateResult.isProgress) {
            Napier.i(
                "Applying Live Update style pkg=$packageName category=${liveUpdateResult.category} " +
                    "progress=${liveUpdateResult.progressPercent}",
                tag = TAG
            )
            ProgressStyleBuilder.applyProgressStyle(context, notificationBuilder, metaInfo, liveUpdateResult)
        }

        val notification = notify(context, notificationId, packageName, notificationBuilder, metaInfo) ?: return
        updateSummaryNotification(context, metaInfo, packageName, notification.group)
    }

    @JvmStatic
    fun getExistsChannelId(context: Context, metaInfo: PushMetaInfo, packageName: String): String {
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val preferredBorrowed = custom.borrowChannelId(null)?.takeIf { it.isNotBlank() }
        if (preferredBorrowed != null) {
            val borrowedChannel = getNotificationManagerEx().findPreferredTargetChannel(packageName, preferredBorrowed)
            if (borrowedChannel != null) {
                logger.d("getExistsChannelId() explicit borrow channel pkg=$packageName channel=${borrowedChannel.id}")
                return borrowedChannel.id
            }
            logger.d("getExistsChannelId() requested borrow channel unavailable pkg=$packageName channel=$preferredBorrowed")
        }
        val fallbackChannelId = NotificationChannelManager.getChannelId(metaInfo, packageName)
        val supportsTargetProvisioning = getNotificationManagerEx().supportsTargetChannelProvisioning(packageName)
        if (supportsTargetProvisioning) {
            NotificationChannelManager.registerChannelIfNeeded(context, metaInfo, packageName)
            logger.d("getExistsChannelId() use managed target channel pkg=$packageName channel=$fallbackChannelId")
            return fallbackChannelId
        }
        NotificationChannelManager.registerChannelIfNeeded(context, metaInfo, packageName)
        logger.d("getExistsChannelId() fallback to local managed channel pkg=$packageName channel=$fallbackChannelId")
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
        if (shouldAutoCancelNotification(metaInfo, notificationBuilder)) {
            notificationBuilder.setAutoCancel(true)
        }
        val notification = ProgressStyleBuilder.buildNotification(context, notificationBuilder)
        val channel = getNotificationManagerEx().getNotificationChannel(packageName, notification.channelId)
        if (!NotificationContentSupport.hasMeaningfulVisibleText(context, packageName, notification, channel)) {
            logger.d("drop contentless notification pkg=$packageName id=$notificationId channel=${notification.channelId}")
            return null
        }
        val tag = MyMIPushNotificationHelper.getNotificationTag(packageName)
        getNotificationManagerEx().notify(packageName, tag, notificationId, notification)
        if (focusBundle != null) {
            FocusNotificationRegistry.registerReplacingUidVariants(
                context,
                focusNotificationKey(context, packageName, notificationId, tag)
            )
        }
        return notification
    }

    @JvmStatic
    internal fun shouldAutoCancelNotification(
        metaInfo: PushMetaInfo,
        notificationBuilder: NotificationCompat.Builder
    ): Boolean {
        return !VoipNotificationHelper.isVoipNotification(metaInfo) &&
            !ProgressStyleBuilder.isLiveUpdate(notificationBuilder)
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
        FocusNotificationRegistry.unregisterAllUidVariants(
            context,
            focusNotificationKey(context, container.packageName, notificationId, tag)
        )
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
        val kindLabel = context.getString(kind.labelRes)
        val title = context.getString(R.string.debug_test_title, kindLabel)
        val description = context.getString(R.string.debug_test_content, kindLabel) + "\n" + java.util.Date()

        val nm = context.getSystemService(android.app.NotificationManager::class.java)
        val mockChannelId = "xmsf_mock_high"
        val mockChannel = android.app.NotificationChannel(
            mockChannelId,
            context.getString(R.string.mock_channel_name),
            android.app.NotificationManager.IMPORTANCE_HIGH,
        )
        nm.createNotificationChannel(mockChannel)

        val id = (System.currentTimeMillis() / 1000L).toInt()
        val builder = NotificationCompat.Builder(context, mockChannelId)
        builder.setSmallIcon(R.drawable.ic_notifications_black_24dp)
        builder.setWhen(System.currentTimeMillis())
        builder.setShowWhen(true)
        builder.setAutoCancel(true)

        val tag = "xmsf_mock_${kind.name}"

        val notifyIntent = LegacyUiEntryPoints.mainActivityIntent(
            context = context,
            startTab = MainActivity.START_TAB_SETTINGS,
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK }
        val notifyPendingIntent = PendingIntent.getActivity(
            context, 0, notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(notifyPendingIntent)

        val targetExtras = Bundle().apply {
            putString("target_package", packageName)
            putString("miui.targetPkg", packageName)
        }
        builder.addExtras(targetExtras)

        when (kind) {
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.PLAIN -> {
                builder.setContentTitle(title)
                builder.setContentText(description)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.BIG_TEXT -> {
                builder.setContentTitle(title)
                builder.setContentText(description)
                val style = NotificationCompat.BigTextStyle()
                style.bigText("$description\n\nLine 2\nLine 3\nLine 4 (expand to view)")
                style.setBigContentTitle(title)
                style.setSummaryText(context.getString(R.string.mock_bigtext_summary))
                builder.setStyle(style)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.BIG_PICTURE -> {
                builder.setContentTitle(title)
                builder.setContentText(description)
                val pic = createDemoBitmap(800, 400, 0xFF2962FFu.toInt())
                val style = NotificationCompat.BigPictureStyle()
                style.bigPicture(pic)
                style.setBigContentTitle(title)
                style.setSummaryText(context.getString(R.string.mock_bigpicture_summary))
                builder.setLargeIcon(pic)
                builder.setStyle(style)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.INBOX -> {
                builder.setContentTitle(title)
                builder.setContentText("$description (3 lines)")
                val style = NotificationCompat.InboxStyle()
                style.setBigContentTitle(title)
                style.addLine(context.getString(R.string.mock_inbox_line1, description))
                style.addLine(context.getString(R.string.mock_inbox_line2))
                style.addLine(context.getString(R.string.mock_inbox_line3))
                style.setSummaryText(context.getString(R.string.mock_inbox_summary))
                builder.setStyle(style)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.MESSAGING -> {
                val person = androidx.core.app.Person.Builder().setName(context.getString(R.string.mock_messaging_user)).build()
                val style = NotificationCompat.MessagingStyle(person)
                style.setConversationTitle(context.getString(R.string.mock_messaging_conversation))
                style.addMessage(description, System.currentTimeMillis(), person)
                style.addMessage(context.getString(R.string.mock_messaging_reply), System.currentTimeMillis() + 1000, person)
                builder.setStyle(style)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.MEDIA -> {
                builder.setContentTitle(title)
                builder.setContentText(context.getString(R.string.mock_media_now_playing, description))
                builder.setOngoing(true)
                builder.addAction(R.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_media_prev), notifyPendingIntent)
                builder.addAction(R.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_media_pause), notifyPendingIntent)
                builder.addAction(R.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_media_next), notifyPendingIntent)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.PROGRESS -> {
                builder.setContentTitle(title)
                builder.setContentText(context.getString(R.string.mock_progress_downloading))
                builder.setProgress(100, 65, false)
                builder.setOngoing(true)
                builder.setAutoCancel(false)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.HEADS_UP -> {
                builder.setContentTitle(title)
                builder.setContentText(description)
                builder.priority = NotificationCompat.PRIORITY_HIGH
                builder.setCategory(Notification.CATEGORY_ALARM)
                builder.setDefaults(NotificationCompat.DEFAULT_ALL)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_BASIC -> {
                builder.setContentTitle(title)
                builder.setContentText(description)
                builder.priority = NotificationCompat.PRIORITY_HIGH
                builder.setOngoing(true)
                val focusBundle = Bundle()
                focusBundle.putString("miui.focus.param", """{"updatable":true,"reopen":"close"}""")
                builder.addExtras(focusBundle)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_WITH_PIC -> {
                builder.setContentTitle(title)
                builder.setContentText(description)
                builder.priority = NotificationCompat.PRIORITY_HIGH
                val pic = createDemoBitmap(400, 400, 0xFFFF6F00u.toInt())
                val focusBundle = Bundle()
                focusBundle.putString("miui.focus.param", """{"updatable":true,"reopen":"close"}""")
                val picsBundle = Bundle()
                picsBundle.putParcelable("miui.focus.pic_main", Icon.createWithBitmap(pic))
                focusBundle.putBundle("miui.focus.pics", picsBundle)
                builder.addExtras(focusBundle)
                builder.setLargeIcon(pic)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.VOIP_INCOMING -> {
                builder.setContentTitle(context.getString(R.string.mock_voip_title))
                builder.setContentText(description)
                builder.priority = NotificationCompat.PRIORITY_MAX
                builder.setCategory(Notification.CATEGORY_CALL)
                builder.setOngoing(true)
                builder.setAutoCancel(false)
                builder.setFullScreenIntent(notifyPendingIntent, true)
                val voipExtras = Bundle()
                voipExtras.putString("notification_style_type", "6")
                voipExtras.putString("msg_busi_type", "voip")
                voipExtras.putString("voip_type", "1")
                builder.addExtras(voipExtras)
                builder.addAction(R.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_voip_accept), notifyPendingIntent)
                builder.addAction(R.drawable.ic_notifications_black_24dp, context.getString(R.string.mock_voip_decline), notifyPendingIntent)
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.LIVE_UPDATE_DELIVERY -> {
                val deliveryTitle = context.getString(R.string.mock_live_update_delivery_title)
                val deliveryDesc = context.getString(R.string.mock_live_update_delivery_desc)
                builder.setContentTitle(deliveryTitle)
                builder.setContentText(deliveryDesc)
                builder.priority = NotificationCompat.PRIORITY_HIGH
                val detection = LiveUpdateDetector.DetectionResult(
                    isProgress = true,
                    category = LiveUpdateDetector.ProgressCategory.DELIVERY,
                    progressPercent = 65,
                    progressText = deliveryDesc,
                    startLabel = "商家",
                    endLabel = "目的地",
                    trackerLabel = "配送中"
                )
                val metaInfo = PushMetaInfo()
                metaInfo.setTitle(deliveryTitle)
                metaInfo.setDescription(deliveryDesc)
                ProgressStyleBuilder.applyProgressStyle(context, builder, metaInfo, detection)
                // Simulate progress updates
                Thread {
                    val steps = listOf(65, 75, 85, 95, 100)
                    val texts = listOf(
                        "骑手已取餐，预计15分钟送达",
                        "骑手距您约800米",
                        "骑手距您约500米",
                        "骑手距您约100米",
                        "骑手已到达，请取餐"
                    )
                    for (i in steps.indices) {
                        Thread.sleep(3000)
                        val updateBuilder = NotificationCompat.Builder(context, mockChannelId).apply {
                            setSmallIcon(R.drawable.ic_notifications_black_24dp)
                            setWhen(System.currentTimeMillis())
                            setContentTitle(deliveryTitle)
                            setContentText(texts[i])
                            setOngoing(true)
                            setProgress(100, steps[i], false)
                            priority = NotificationCompat.PRIORITY_HIGH
                        }
                        val updateDetection = detection.copy(
                            progressPercent = steps[i],
                            progressText = texts[i]
                        )
                        val updateMetaInfo = PushMetaInfo()
                        updateMetaInfo.setTitle(deliveryTitle)
                        updateMetaInfo.setDescription(texts[i])
                        ProgressStyleBuilder.applyProgressStyle(context, updateBuilder, updateMetaInfo, updateDetection)
                        nm.notify(tag, id, ProgressStyleBuilder.buildNotification(context, updateBuilder))
                    }
                    // Auto-cancel when complete
                    Thread.sleep(2000)
                    nm.cancel(tag, id)
                }.start()
            }
        }

        nm.notify(tag, id, ProgressStyleBuilder.buildNotification(context, builder))
        if (kind == io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_BASIC ||
            kind == io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_WITH_PIC) {
            val actualPkg = context.packageName
            val key = focusNotificationKey(context, actualPkg, id, tag)
            FocusNotificationRegistry.registerReplacingUidVariants(context, key)
            // Simulate refresh effect: update notification every 2 seconds for 5 times
            Thread {
                val steps = listOf(
                    "已接单，骑手正在赶往商家",
                    "骑手已到店，等待取餐",
                    "骑手已取餐，正在配送中",
                    "骑手距您约500米",
                    "骑手已到达，请取餐",
                )
                for (i in steps.indices) {
                    Thread.sleep(2000)
                    builder.setContentText(steps[i])
                    builder.setWhen(System.currentTimeMillis())
                    nm.notify(tag, id, builder.build())
                }
            }.start()
        }
    }

    private fun focusNotificationKey(context: Context, packageName: String, notificationId: Int, tag: String?): String {
        val uid = resolveNotificationUid(context, packageName)
        return "0|$packageName|$notificationId|$tag|$uid"
    }

    private fun resolveNotificationUid(context: Context, packageName: String): Int {
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0)).uid
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0).uid
            }
        }.getOrElse { error ->
            val fallback = if (packageName == context.packageName) android.os.Process.myUid() else 0
            Napier.w("failed to resolve uid for focus notification pkg=$packageName fallback=$fallback", error, tag = TAG)
            fallback
        }
    }

    private fun createDemoBitmap(width: Int, height: Int, color: Int): Bitmap {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        canvas.drawColor(color)
        val paint = android.graphics.Paint().apply {
            this.color = Color.WHITE
            textSize = (height / 4).toFloat()
            isAntiAlias = true
            textAlign = android.graphics.Paint.Align.CENTER
        }
        canvas.drawText("XMSF", (width / 2).toFloat(), height / 2 + paint.textSize / 3, paint)
        return bitmap
    }
}
