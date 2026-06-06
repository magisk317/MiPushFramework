package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

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
import io.github.magisk317.mipush.common.NotificationStyle
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.IconConfigurations
import io.github.magisk317.mipush.utils.ColorUtil
import io.github.magisk317.mipush.common.utils.CustomConfiguration
import io.github.magisk317.mipush.common.utils.ImgUtils
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import io.github.magisk317.mipush.runtime.PushRuntime

object NotificationController {
    private const val TAG = "NotificationController"
    private const val FOCUS_PARAM = "miui.focus.param"
    private const val FOCUS_PICS = "miui.focus.pics"
    private const val PIC_ICON = "miui.focus.pic_mipush_icon"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val ACTION_SHOW_ISLAND = "io.github.magisk317.mipush.action.SHOW_ISLAND"
    private const val ACTION_CANCEL_ISLAND = "io.github.magisk317.mipush.action.CANCEL_ISLAND"
    private const val EXTRA_ALLOW_ISLAND_PROXY = "mipush_island_allow_proxy"
    private const val EXTRA_NOTIFICATION_ID = "notification_id"
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
        val groupCount = getNotificationCountOfGroup(packageName, groupId)
        Napier.d("updateSummaryNotification pkg=$packageName groupId=$groupId groupCount=$groupCount summaryId=${groupId.hashCode()}", tag = TAG)
        if (groupCount <= 1) {
            Napier.d("updateSummaryNotification cancel summary pkg=$packageName summaryId=${groupId.hashCode()} groupCount=$groupCount", tag = TAG)
            getNotificationManagerEx().cancel(packageName, null, groupId.hashCode())
            return
        }
        val builder = NotificationCompat.Builder(context, getExistsChannelId(context, metaInfo, packageName))
        builder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        builder.setCategory(Notification.CATEGORY_EVENT).setGroupSummary(true).setGroup(groupId)
        builder.setContentTitle(context.getString(R.string.group_summary_title, groupCount))
        notify(
            context,
            groupId.hashCode(),
            packageName,
            builder,
            metaInfo
        )
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
            val n = safeNotification.notification
            val inGroup = groupId == n.group
            if (inGroup) notificationCntInGroup++
            logD("getNotificationCountOfGroup pkg=$packageName id=${safeNotification.id} tag=${safeNotification.tag} group=${n.group} targetGroup=$groupId match=$inGroup")
        }
        Napier.d("getNotificationCountOfGroup result pkg=$packageName groupId=$groupId total=${activeNotifications.size} inGroup=$notificationCntInGroup", tag = TAG)
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

        val notification = notify(context, notificationId, packageName, notificationBuilder, metaInfo)
        if (notification == null) {
            Napier.d("publish skipped pkg=$packageName id=$notificationId (contentless, channel, or publish issue)", tag = TAG)
            return
        }
        Napier.d("publish posted pkg=$packageName id=$notificationId group=${notification.group} tag=${MyMIPushNotificationHelper.getNotificationTag(packageName)}", tag = TAG)
        updateSummaryNotification(context, metaInfo, packageName, notification.group)
    }

    @JvmStatic
    fun getExistsChannelId(context: Context, metaInfo: PushMetaInfo, packageName: String): String {
        val custom = XMPushUtils.getConfiguration(metaInfo)
        val preferredBorrowed = custom.borrowChannelId(null)?.takeIf { it.isNotBlank() }
        if (preferredBorrowed != null) {
            val borrowedChannel = getNotificationManagerEx().findPreferredTargetChannel(packageName, preferredBorrowed)
            if (borrowedChannel != null) {
                logD("getExistsChannelId() explicit borrow channel pkg=$packageName channel=${borrowedChannel.id}")
                return borrowedChannel.id
            }
            logD("getExistsChannelId() requested borrow channel unavailable pkg=$packageName channel=$preferredBorrowed")
        }
        val fallbackChannelId = NotificationChannelManager.getChannelId(metaInfo, packageName)
        val supportsTargetProvisioning = getNotificationManagerEx().supportsTargetChannelProvisioning(packageName)
        if (supportsTargetProvisioning) {
            NotificationChannelManager.registerChannelIfNeeded(context, metaInfo, packageName)
            logD("getExistsChannelId() use managed target channel pkg=$packageName channel=$fallbackChannelId")
            return fallbackChannelId
        }
        NotificationChannelManager.registerChannelIfNeeded(context, metaInfo, packageName)
        logD("getExistsChannelId() fallback to local managed channel pkg=$packageName channel=$fallbackChannelId")
        return fallbackChannelId
    }

    private fun notify(
        context: Context,
        notificationId: Int,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder,
        metaInfo: PushMetaInfo,
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

        val previewNotification = ProgressStyleBuilder.buildNotification(context, notificationBuilder)
        val islandOptions = MiPushIslandPreferences.read(context, packageName)
        val generatedFocusCandidate = islandOptions.canBuildFocusPayload && !previewNotification.isGroupSummary()
        val rawConfiguredFocusParam = configuration.focusParam(null)
        val preliminaryFocusPlan = FocusSemanticTranslator.plan(
            context = context,
            metaInfo = metaInfo,
            packageName = packageName,
            configuredFocusParam = rawConfiguredFocusParam,
            generatedFocusParam = null,
            generatedFocusCandidate = generatedFocusCandidate,
        )
        val configuredFocusBundle = if (
            generatedFocusCandidate &&
            preliminaryFocusPlan.attachMiuiFocusExtras
        ) {
            buildConfiguredFocusBundle(
                context = context,
                packageName = packageName,
                largeIcon = largeIcon,
                notificationIcon = previewNotification.getLargeIconCompat(),
                configuration = configuration,
            ) { url -> getBitmapFromUri(context, url, 200 * KIB) }
        } else {
            null
        }
        val generatedFocusBundle = if (
            rawConfiguredFocusParam.isNullOrBlank() &&
            configuredFocusBundle == null &&
            generatedFocusCandidate
        ) {
            MiPushIslandPayloadBuilder.build(
                context = context,
                metaInfo = metaInfo,
                packageName = packageName,
                largeIcon = largeIcon,
                notificationId = notificationId,
                notificationIcon = previewNotification.getLargeIconCompat(),
                options = islandOptions,
                liveUpdateResult = preliminaryFocusPlan.semantic?.toDetectionResult()
                    ?: preliminaryFocusPlan.nativeDetection,
            )
        } else {
            null
        }
        val focusPlan = generatedFocusBundle
            ?.getString(FOCUS_PARAM)
            ?.let { generatedFocusParam ->
                FocusSemanticTranslator.plan(
                    context = context,
                    metaInfo = metaInfo,
                    packageName = packageName,
                    configuredFocusParam = rawConfiguredFocusParam,
                    generatedFocusParam = generatedFocusParam,
                    generatedFocusCandidate = generatedFocusCandidate,
                )
            }
            ?: preliminaryFocusPlan

        val tag = MyMIPushNotificationHelper.getNotificationTag(packageName)
        val nativeFeature = NativeNotificationFeatureBuilder.apply(
            context = context,
            builder = notificationBuilder,
            metaInfo = metaInfo,
            packageName = packageName,
            focusPlan = focusPlan,
            contentIntent = previewNotification.contentIntent,
            notificationKey = NativeNotificationFeatureBuilder.notificationKey(packageName, notificationId, tag),
        )

        if (configuredFocusBundle != null && focusPlan.attachMiuiFocusExtras) {
            notificationBuilder.addExtras(configuredFocusBundle)
            notificationBuilder.priority = NotificationCompat.PRIORITY_HIGH
        } else if (focusPlan.allowIslandProxy && !NotificationManagerEx.isHooked) {
            notificationBuilder.addExtras(
                Bundle().apply {
                    putBoolean(EXTRA_ALLOW_ISLAND_PROXY, true)
                }
            )
        }

        NotificationSortFilter.attachDeleteIntentIfNeeded(
            context,
            notificationBuilder,
            packageName,
            rawConfiguredFocusParam.takeIf { focusPlan.attachMiuiFocusExtras },
            notificationId
        )
        if (shouldAutoCancelNotification(metaInfo, notificationBuilder, nativeFeature)) {
            notificationBuilder.setAutoCancel(true)
        }
        val notification = NativeNotificationFeatureBuilder.buildNotification(context, notificationBuilder, nativeFeature)
        val channel = getNotificationManagerEx().getNotificationChannel(packageName, notification.channelId)
        if (!NotificationChannelManager.isNotificationChannelEnabled(channel)) {
            logD("drop disabled channel notification pkg=$packageName id=$notificationId channel=${notification.channelId}")
            NativeNotificationFeatureBuilder.releaseMediaSession(packageName, notificationId, tag)
            return null
        }
        if (!NotificationContentSupport.hasMeaningfulVisibleText(context, packageName, notification, channel)) {
            logD("drop contentless notification pkg=$packageName id=$notificationId channel=${notification.channelId}")
            NativeNotificationFeatureBuilder.releaseMediaSession(packageName, notificationId, tag)
            return null
        }
        if (configuredFocusBundle != null && focusPlan.attachMiuiFocusExtras) {
            FocusNotificationRegistry.registerReplacingUidVariants(
                context,
                focusNotificationKey(context, packageName, notificationId, tag)
            )
        }
        val notificationToPost = if (focusPlan.allowIslandProxy &&
            generatedFocusBundle != null &&
            sendGeneratedIslandProxy(
                context = context,
                metaInfo = metaInfo,
                packageName = packageName,
                notificationId = notificationId,
                tag = tag,
                notification = notification,
                options = islandOptions,
            )
        ) {
            quietGeneratedIslandStatusBarNotification(notificationBuilder)
            NativeNotificationFeatureBuilder.buildNotification(
                context,
                notificationBuilder,
                NativeNotificationFeatureBuilder.Result.NONE,
            )
        } else {
            notification
        }
        if (!getNotificationManagerEx().notify(packageName, tag, notificationId, notificationToPost)) {
            Napier.w(
                "publish failed pkg=$packageName id=$notificationId tag=$tag channel=${notificationToPost.channelId}",
                tag = TAG
            )
            PushRuntime.observeNotificationEvent(packageName, "notification_publish_failed", "NotificationController.publish")
            NativeNotificationFeatureBuilder.releaseMediaSession(packageName, notificationId, tag)
            return null
        }
        PushRuntime.observeNotificationEvent(packageName, "notification_publish_posted", "NotificationController.publish")
        return notificationToPost
    }

    private fun quietGeneratedIslandStatusBarNotification(notificationBuilder: NotificationCompat.Builder) {
        notificationBuilder.setDefaults(0)
        notificationBuilder.setOnlyAlertOnce(true)
    }

    private fun sendGeneratedIslandProxy(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
        notificationId: Int,
        tag: String?,
        notification: Notification,
        options: MiPushIslandOptions,
    ): Boolean {
        if (!NotificationManagerEx.isHooked) {
            return false
        }
        val title = firstText(
            notification.extras,
            Notification.EXTRA_TITLE,
            Notification.EXTRA_TITLE_BIG,
        ) ?: notification.tickerText?.toString()
            ?: metaInfo.title?.takeIf { it.isNotBlank() }
            ?: metaInfo.description?.takeIf { it.isNotBlank() }
            ?: return false
        val content = firstText(
            notification.extras,
            Notification.EXTRA_TEXT,
            Notification.EXTRA_BIG_TEXT,
            Notification.EXTRA_SUB_TEXT,
            Notification.EXTRA_INFO_TEXT,
        ) ?: metaInfo.description?.takeIf { it.isNotBlank() }
            ?: title
        val appContext = context.applicationContext ?: context
        val proxyId = islandProxyNotificationId(packageName, notificationId, tag)
        return runCatching {
            appContext.sendBroadcast(
                Intent(ACTION_SHOW_ISLAND).apply {
                    setPackage(SYSTEM_UI_PACKAGE)
                    putExtra("title", title)
                    putExtra("content", content)
                    putExtra(
                        "icon",
                        MiPushIslandPayloadBuilder.resolveNotificationIcon(
                            context = appContext,
                            packageName = packageName,
                            notificationIcon = notification.getLargeIconCompat(),
                            largeIcon = null,
                        ),
                    )
                    putExtra("notificationId", proxyId)
                    putExtra("timeoutSecs", options.timeoutSecs)
                    putExtra("firstFloat", options.firstFloat)
                    putExtra("enableFloat", options.enableFloat)
                    putExtra("showNotification", options.showNotification)
                    putExtra("sourcePackage", packageName)
                    putExtra("sourceChannelId", notification.channelId)
                    putExtra("contentIntent", notification.contentIntent)
                    putExtra("isOngoing", notification.flags and Notification.FLAG_ONGOING_EVENT != 0)
                    putExtra("showIslandIcon", true)
                    putExtra("clearBeforePost", true)
                },
            )
        }.fold(
            onSuccess = {
                PushRuntime.observeNotificationEvent(packageName, "notification_island_proxy_posted", "NotificationController.publish")
                Napier.d("posted island proxy pkg=$packageName id=$notificationId proxyId=$proxyId", tag = TAG)
                true
            },
            onFailure = {
                PushRuntime.observeNotificationEvent(packageName, "notification_island_proxy_failed", "NotificationController.publish")
                Napier.w("island proxy failed pkg=$packageName id=$notificationId: ${it.message}", it, tag = TAG)
                false
            },
        )
    }

    @JvmStatic
    internal fun shouldAutoCancelNotification(
        metaInfo: PushMetaInfo,
        notificationBuilder: NotificationCompat.Builder,
        nativeFeature: NativeNotificationFeatureBuilder.Result = NativeNotificationFeatureBuilder.Result.NONE,
    ): Boolean {
        return !VoipNotificationHelper.isVoipNotification(metaInfo) &&
            !nativeFeature.preventsAutoCancel &&
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
            val safeUri = if (iconUri.startsWith("http://")) {
                iconUri.replaceFirst("http://", "https://")
            } else {
                iconUri
            }
            if (safeUri.startsWith("http")) {
                val result = MyNotificationIconHelper.getIconFromUrl(context, safeUri, maxDownloadBytes)
                bitmap = result.bitmap
            } else {
                bitmap = MyNotificationIconHelper.getIconFromUri(context, safeUri)
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
        focusBundle.putString(FOCUS_PARAM, focusParam)
        val picsBundle = Bundle()
        for ((key, url) in collectFocusPicUris(configuration)) {
            focusBundle.putString(key, url)
            val bitmap = bitmapLoader(url)
            if (bitmap != null) {
                picsBundle.putParcelable(key, Icon.createWithBitmap(bitmap))
            }
        }
        if (!picsBundle.isEmpty) {
            focusBundle.putBundle(FOCUS_PICS, picsBundle)
        }
        return focusBundle
    }

    internal fun buildConfiguredFocusBundle(
        context: Context,
        packageName: String,
        largeIcon: Bitmap?,
        notificationIcon: Icon?,
        configuration: CustomConfiguration,
        bitmapLoader: (String) -> Bitmap?,
    ): Bundle? {
        val icon = MiPushIslandPayloadBuilder.resolveNotificationIcon(
            context,
            packageName,
            notificationIcon,
            largeIcon,
        )
        val configured = buildFocusBundle(configuration, bitmapLoader)
        if (configured != null) {
            val pics = configured.getBundle(FOCUS_PICS) ?: Bundle().also {
                configured.putBundle(FOCUS_PICS, it)
            }
            configured.putString(PIC_ICON, PIC_ICON)
            if (!pics.containsKey(PIC_ICON)) {
                pics.putParcelable(PIC_ICON, icon)
            }
            return configured
        }
        return null
    }

    private fun Notification.isGroupSummary(): Boolean {
        return flags and Notification.FLAG_GROUP_SUMMARY != 0
    }

    private fun Notification.getLargeIconCompat(): Icon? {
        return runCatching { getLargeIcon() }.getOrNull()
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
        NativeNotificationFeatureBuilder.releaseMediaSession(container.packageName, notificationId, tag)
        cancelGeneratedIslandProxy(context, container.packageName, notificationId, tag)
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
            Context.CONTEXT_IGNORE_SECURITY
        )
        if (pkgContext === context) {
            // Means it failed or not hooked
            setAppIconSmallIcon(context, packageName, notificationBuilder)
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
                return color
            }
            setAppIconSmallIcon(context, packageName, notificationBuilder)
        }
        return color
    }

    private fun setAppIconSmallIcon(
        context: Context,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder,
    ): Boolean {
        val iconBitmap = Global.iconCache().getRawIconBitmap(context, packageName)
            ?: return false
        notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(iconBitmap))
        return true
    }

    private fun cancelGeneratedIslandProxy(
        context: Context,
        packageName: String,
        notificationId: Int,
        tag: String?,
    ) {
        if (!NotificationManagerEx.isHooked) {
            return
        }
        runCatching {
            (context.applicationContext ?: context).sendBroadcast(
                Intent(ACTION_CANCEL_ISLAND).apply {
                    setPackage(SYSTEM_UI_PACKAGE)
                    putExtra(EXTRA_NOTIFICATION_ID, islandProxyNotificationId(packageName, notificationId, tag))
                },
            )
        }.onFailure {
            Napier.w("cancel island proxy failed pkg=$packageName id=$notificationId: ${it.message}", it, tag = TAG)
        }
    }

    private fun firstText(extras: Bundle?, vararg keys: String): String? {
        if (extras == null) return null
        return keys.firstNotNullOfOrNull { key ->
            extras.getCharSequence(key)?.toString()?.takeIf { it.isNotBlank() }
        }
    }

    @Suppress("UNUSED_PARAMETER")
    private fun islandProxyNotificationId(packageName: String, notificationId: Int, tag: String?): Int {
        return "mipush_island:$packageName".hashCode()
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
        PushRuntime.observeNotificationEvent(packageName, "mock_test_build_start", "NotificationController.testMock")
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
        NativeNotificationFeatureBuilder.releaseMediaSessionsForTag(packageName, tag)
        Napier.d("mock test build kind=${kind.name} pkg=$packageName id=$id tag=$tag", tag = TAG)

        val notifyIntent = LegacyUiEntryPoints.mainActivityIntent(
            context = context,
            startRoute = "events",
        ).apply { flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP }
        val notifyPendingIntent = PendingIntent.getActivity(
            context, 0, notifyIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        builder.setContentIntent(notifyPendingIntent)

        val targetExtras = Bundle().apply {
            putString("target_package", packageName)
            putString("miui.targetPkg", packageName)
            putString("xmsf_target_package", packageName)
        }
        builder.addExtras(targetExtras)

        var nativeFeature = NativeNotificationFeatureBuilder.Result.NONE
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
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.DYNAMIC_ISLAND -> {
                val mockFocusResult = handleMockFocusSemantic(
                    context = context,
                    builder = builder,
                    kind = kind,
                    packageName = packageName,
                    notificationId = id,
                    notificationTag = tag,
                    title = title,
                    description = description,
                    contentIntent = notifyPendingIntent,
                    style = NotificationStyle.PROMO,
                    islandOuterGlow = true,
                )
                if (mockFocusResult.handled) {
                    return
                }
                nativeFeature = mockFocusResult.nativeFeature
            }
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_NOTIFICATION,
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_MESSAGE,
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_BANNER,
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_ALERT,
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_PROMO,
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_MEDIA,
            io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind.FOCUS_PROGRESS -> {
                val focusStyle = kind.focusTemplateStyle ?: NotificationStyle.GENERAL
                val focusSpec = mockFocusSpec(kind, title, description)
                val mockFocusResult = handleMockFocusSemantic(
                    context = context,
                    builder = builder,
                    kind = kind,
                    packageName = packageName,
                    notificationId = id,
                    notificationTag = tag,
                    title = focusSpec.title,
                    description = focusSpec.content,
                    contentIntent = notifyPendingIntent,
                    style = focusStyle,
                    isOngoing = focusStyle == NotificationStyle.MEDIA || focusStyle == NotificationStyle.PROGRESS,
                )
                if (mockFocusResult.handled) {
                    return
                }
                nativeFeature = mockFocusResult.nativeFeature
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

        val notification = NativeNotificationFeatureBuilder.buildNotification(context, builder, nativeFeature)
        nm.notify(tag, id, notification)
        Napier.d(
            "mock test posted kind=${kind.name} pkg=$packageName id=$id tag=$tag " +
                "focus=${notification.extras.containsKey(FOCUS_PARAM)} " +
                "contentIntent=${notification.contentIntent != null} nativeFeature=${nativeFeature.feature}",
            tag = TAG,
        )
        PushRuntime.observeNotificationEvent(packageName, "mock_test_notification_posted", "NotificationController.testMock")
    }

    private data class MockFocusSemanticResult(
        val handled: Boolean = false,
        val nativeFeature: NativeNotificationFeatureBuilder.Result = NativeNotificationFeatureBuilder.Result.NONE,
    )

    private fun handleMockFocusSemantic(
        context: Context,
        builder: NotificationCompat.Builder,
        kind: io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind,
        packageName: String,
        notificationId: Int,
        notificationTag: String,
        title: String,
        description: String,
        contentIntent: PendingIntent,
        style: NotificationStyle,
        isOngoing: Boolean = false,
        islandOuterGlow: Boolean = false,
    ): MockFocusSemanticResult {
        builder.setContentTitle(title)
        builder.setContentText(description)
        if (isOngoing) {
            builder.setOngoing(true)
            builder.setAutoCancel(false)
        }

        val metaInfo = PushMetaInfo().apply {
            setTitle(title)
            setDescription(description)
        }
        val options = MiPushIslandPreferences.read(context, packageName)
        val generatedFocusParam = MiPushIslandPayloadBuilder.buildFocusParam(
            context = context,
            metaInfo = metaInfo,
            packageName = packageName,
            largeIcon = null,
            options = options,
            styleOverride = style,
        )
        val focusPlan = FocusSemanticTranslator.plan(
            context = context,
            metaInfo = metaInfo,
            packageName = packageName,
            configuredFocusParam = null,
            generatedFocusParam = generatedFocusParam,
            generatedFocusCandidate = generatedFocusParam != null,
        )

        if (focusPlan.allowIslandProxy && NotificationManagerEx.isHooked) {
            Napier.d(
                "mock test island broadcast kind=${kind.name} style=$style pkg=$packageName " +
                    "id=$notificationId reason=${focusPlan.reason}",
                tag = TAG,
            )
            PushRuntime.observeNotificationEvent(
                packageName,
                "mock_test_island_broadcast",
                "NotificationController.testMock",
            )
            context.sendMockIslandBroadcast(
                title = title,
                description = description,
                sourcePackage = packageName,
                notificationId = notificationId,
                contentIntent = contentIntent,
                style = style,
                smallOnly = false,
                isOngoing = isOngoing,
                islandOuterGlow = islandOuterGlow,
            )
            return MockFocusSemanticResult(handled = true)
        }

        if (focusPlan.allowIslandProxy) {
            builder.addExtras(
                Bundle().apply {
                    putBoolean(EXTRA_ALLOW_ISLAND_PROXY, true)
                },
            )
            return MockFocusSemanticResult()
        }

        val nativeFeature = NativeNotificationFeatureBuilder.apply(
            context = context,
            builder = builder,
            metaInfo = metaInfo,
            packageName = packageName,
            focusPlan = focusPlan,
            styleOverride = style,
            contentIntent = contentIntent,
            notificationKey = NativeNotificationFeatureBuilder.notificationKey(
                packageName,
                notificationId,
                notificationTag,
            ),
        )

        Napier.d(
            "mock test focus semantic kind=${kind.name} style=$style pkg=$packageName " +
                "id=$notificationId reason=${focusPlan.reason} nativeFeature=${nativeFeature.feature}",
            tag = TAG,
        )
        return MockFocusSemanticResult(nativeFeature = nativeFeature)
    }

    private data class MockFocusSpec(
        val title: String,
        val content: String,
    )

    private fun mockFocusSpec(
        kind: io.github.magisk317.mipush.feature.diagnostic.MockNotificationKind,
        fallbackTitle: String,
        fallbackContent: String,
    ): MockFocusSpec {
        return when (kind.focusTemplateStyle) {
            NotificationStyle.MESSAGE -> MockFocusSpec(
                title = "Alice",
                content = "focus chat message: $fallbackContent",
            )
            NotificationStyle.BANNER -> MockFocusSpec(
                title = fallbackTitle,
                content = "featured banner update: $fallbackContent",
            )
            NotificationStyle.ALERT -> MockFocusSpec(
                title = "focus countdown reminder",
                content = "countdown 15 minutes before the meeting",
            )
            NotificationStyle.PROMO -> MockFocusSpec(
                title = "focus coupon sale",
                content = "coupon discount 65% off for template testing",
            )
            NotificationStyle.MEDIA -> MockFocusSpec(
                title = "focus now playing",
                content = "media cover template: $fallbackContent",
            )
            NotificationStyle.PROGRESS -> MockFocusSpec(
                title = "focus download progress",
                content = "download progress 65%",
            )
            else -> MockFocusSpec(
                title = fallbackTitle,
                content = fallbackContent,
            )
        }
    }

    private fun Context.sendMockIslandBroadcast(
        title: String,
        description: String,
        sourcePackage: String,
        notificationId: Int,
        contentIntent: PendingIntent,
        style: NotificationStyle = NotificationStyle.GENERAL,
        smallOnly: Boolean = false,
        isOngoing: Boolean = false,
        islandOuterGlow: Boolean = true,
    ) {
        val options = MiPushIslandPreferences.read(this, sourcePackage)
        val icon = MiPushIslandPayloadBuilder.resolveNotificationIcon(this, sourcePackage, null)
        Napier.d(
            "mock island broadcast sourcePkg=$sourcePackage notificationId=$notificationId " +
                "timeout=${options.timeoutSecs} firstFloat=${options.firstFloat} enableFloat=${options.enableFloat}",
            tag = TAG,
        )
        sendBroadcast(
            Intent(ACTION_SHOW_ISLAND).apply {
                setPackage(SYSTEM_UI_PACKAGE)
                putExtra("title", title)
                putExtra("content", description)
                putExtra("icon", icon)
                putExtra("notificationId", notificationId)
                putExtra("timeoutSecs", options.timeoutSecs)
                putExtra("firstFloat", options.firstFloat)
                putExtra("enableFloat", options.enableFloat)
                putExtra("showNotification", false)
                putExtra("sourcePackage", sourcePackage)
                putExtra("sourceChannelId", "mipush_mock_island")
                putExtra("contentIntent", contentIntent)
                putExtra("isOngoing", isOngoing)
                putExtra("showIslandIcon", true)
                putExtra("clearBeforePost", true)
                putExtra("islandOuterGlow", islandOuterGlow)
                putExtra("style", style.name)
                putExtra("smallOnly", smallOnly)
            },
        )
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
