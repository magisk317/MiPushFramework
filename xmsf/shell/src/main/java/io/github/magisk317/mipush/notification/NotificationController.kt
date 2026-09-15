package io.github.magisk317.mipush.notification
import io.github.magisk317.mipush.common.R as CommonR

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import io.github.magisk317.mipush.common.notification.iconpack.ICON_PACK_SOURCE_IDENTITY_EXTRA
import io.github.magisk317.mipush.common.notification.iconpack.IconPackResolver
import io.github.magisk317.mipush.common.notification.iconpack.ResolveFailure
import io.github.magisk317.mipush.common.notification.iconpack.ResolveResult
import io.github.magisk317.mipush.common.notification.iconpack.thirdPartyPackSourceIdentity
import co.touchlab.kermit.Logger
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.service.runtime.MIPushNotificationPublishHelper
import io.github.magisk317.mipush.service.runtime.MIPushNotificationStyleSupport
import io.github.magisk317.mipush.service.runtime.ExtensionNotificationContract
import com.xiaomi.push.service.MyNotificationIconHelper
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.NotificationGroupHelper
import com.xiaomi.push.service.NotificationManagerHelper
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.stock.StockNotificationMetadataBridge
import io.github.magisk317.mipush.notification.policy.NotificationStyle
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.notification.NotificationContentSupport
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import io.github.magisk317.mipush.utils.ColorUtil
import io.github.magisk317.mipush.notification.policy.CustomConfiguration
import io.github.magisk317.mipush.common.utils.ImgUtils
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.platform.support.ManagerUiEntryPoints
import io.github.magisk317.mipush.runtime.PushRuntime

object NotificationController {
    enum class PublishResult {
        Posted,
        ChannelDisabled,
        SuppressedByPolicy,
        Failed,
    }

    private sealed interface NotifyResult {
        data class Posted(val notification: Notification) : NotifyResult
        data object SuppressedByPolicy : NotifyResult
        data object ChannelDisabled : NotifyResult
        data object Failed : NotifyResult
    }

    private const val TAG = "NotificationController"
    private val iconPackResolver = IconPackResolver()
    private const val FOCUS_PARAM = "miui.focus.param"
    private const val FOCUS_PICS = "miui.focus.pics"
    private const val PIC_ICON = "miui.focus.pic_mipush_icon"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val ACTION_SHOW_ISLAND = "io.github.magisk317.mipush.action.SHOW_ISLAND"
    private const val EXTRA_ALLOW_ISLAND_PROXY = "mipush_island_allow_proxy"
    private const val NOTIFICATION_LARGE_ICON = "mipush_notification"
    private const val NOTIFICATION_SMALL_ICON = "mipush_small_notification"
    private const val KIB = 1024
    private const val PER_USER_RANGE = 100_000L
    const val CHANNEL_WARN = "warn"

    @JvmStatic
    fun getNotificationManagerEx(): NotificationManagerEx = NotificationManagerEx

    @JvmStatic
    fun publish(
        context: Context,
        metaInfo: PushMetaInfo,
        notificationId: Int,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder
    ): PublishResult {
        val startedAt = System.nanoTime()
        var channelId = getExistsChannelId(context, metaInfo, packageName)
        fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
            val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
            val attrs = mutableMapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "main",
                "target_package" to packageName,
                "channel_id" to channelId,
                "source" to if (metaInfo.isMockReplay()) "mock_replay" else "server",
                "commit" to BuildConfig.GIT_COMMIT,
            )
            if (reason != null) {
                attrs["reason"] = reason
            }
            MagiskOtel.event(
                name = "push.dispatch",
                attributes = attrs,
                statusOk = statusOk,
            )
        }

        notificationBuilder.setChannelId(channelId)
        notificationBuilder.setGroupAlertBehavior(NotificationCompat.GROUP_ALERT_CHILDREN)
        notificationBuilder.setDefaults(Notification.DEFAULT_ALL)
        if (!VoipNotificationHelper.isVoipNotification(metaInfo)) {
            notificationBuilder.priority = NotificationCompat.PRIORITY_HIGH
        }

        var channel = getNotificationManagerEx().getNotificationChannel(packageName, channelId)
        if (channel == null) {
            val fallback = getNotificationManagerEx().findPublishFallbackChannel(packageName, channelId)
            if (fallback != null) {
                logW(
                    "publish remapped unavailable channel pkg=$packageName id=$notificationId " +
                        "requested=$channelId actual=${fallback.id} " +
                        "source=${if (metaInfo.isMockReplay()) "mock_replay" else "server"}",
                )
                channelId = fallback.id
                notificationBuilder.setChannelId(channelId)
                channel = fallback
            }
        }
        when {
            channel == null -> {
                Logger.withTag(TAG).d {
                    "publish skipped unavailable matched channel pkg=$packageName id=$notificationId " +
                        "channel=$channelId source=${if (metaInfo.isMockReplay()) "mock_replay" else "server"}"
                }
                emit(result = "skip", reason = "matched_channel_unavailable")
                return PublishResult.Failed
            }
            channel.importance == NotificationManager.IMPORTANCE_NONE -> {
                Logger.withTag(TAG).d {
                    "publish skipped disabled matched channel pkg=$packageName id=$notificationId " +
                        "channel=$channelId importance=${channel.importance} " +
                        "source=${if (metaInfo.isMockReplay()) "mock_replay" else "server"}"
                }
                emit(result = "skip", reason = "matched_channel_disabled")
                return PublishResult.ChannelDisabled
            }
        }

        val description = metaInfo.description
        if (SweetTagHandler.containsFtTag(description)) {
            notificationBuilder.setContentText(SweetTagHandler.renderFtHtmlIfNeeded(description))
        }

        val notifyResult = notify(context, notificationId, packageName, notificationBuilder, metaInfo)
        if (notifyResult === NotifyResult.SuppressedByPolicy) {
            emit(result = "skip", reason = "original_notification_disabled")
            return PublishResult.SuppressedByPolicy
        }
        if (notifyResult === NotifyResult.ChannelDisabled) {
            emit(result = "skip", reason = "matched_channel_disabled")
            return PublishResult.ChannelDisabled
        }
        val notification = (notifyResult as? NotifyResult.Posted)?.notification
        if (notification == null) {
            Logger.withTag(TAG).d { "publish skipped pkg=$packageName id=$notificationId (contentless, channel, or publish issue)" }
            emit(result = "skip", reason = "notify_null")
            return PublishResult.Failed
        }
        Logger.withTag(TAG).d {
            "publish posted pkg=$packageName id=$notificationId group=${notification.group} " +
                "tag=${MIPushNotificationPublishHelper.getNotificationTag(packageName)}"
        }
        if (MIUIUtils.isMIUI() && MIUIUtils.isXMSF(context) && !metaInfo.isMockReplay()) {
            NotificationGroupHelper.getInstance().onNotificationNotify(
                context,
                notificationId,
                notification,
            )
        }
        emit(result = "ok")
        return PublishResult.Posted
    }

    @JvmStatic
    fun getExistsChannelId(context: Context, metaInfo: PushMetaInfo, packageName: String): String =
        NotificationChannelResolutionSupport.getExistsChannelId(context, metaInfo, packageName)

    /** Lookup-only channel resolution for read paths; never creates groups or channels. */
    internal fun findExistingChannelId(
        context: Context,
        metaInfo: PushMetaInfo,
        packageName: String,
    ): String? = NotificationChannelResolutionSupport.findExistingChannelId(context, metaInfo, packageName)

    internal fun selectManagedChannelId(
        stockChannelId: String,
        stockChannelExists: Boolean,
        legacyChannelId: String,
        legacyChannelExists: Boolean,
    ): String = NotificationChannelResolutionSupport.selectManagedChannelId(
        stockChannelId,
        stockChannelExists,
        legacyChannelId,
        legacyChannelExists,
    )

    private fun notify(
        context: Context,
        notificationId: Int,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder,
        metaInfo: PushMetaInfo,
        applyPayloadDecorations: Boolean = true,
    ): NotifyResult {
        val userId = resolveNotificationUserId(context, packageName)
        val islandOptions = MiPushIslandPreferences.read(context, packageName, userId)
        val isMockReplay = metaInfo.isMockReplay()
        val extras = Bundle()
        addTargetPackageIdentity(extras, packageName)
        if (applyPayloadDecorations) {
            StockNotificationMetadataBridge.apply(metaInfo, extras)
        }
        notificationBuilder.addExtras(extras)
        val color = applyStatusBarIcon(
            context,
            packageName,
            notificationBuilder,
            islandOptions.colorStatusBarIcon,
        )
        val configuration = XMPushUtils.getConfiguration(metaInfo)
        val largeIcon = if (
            applyPayloadDecorations &&
            shouldAttachPayloadLargeIcon(isMockReplay, islandOptions.colorStatusBarIcon)
        ) {
            val iconUri = configuration.notificationLargeIconUri(null)
            // Stock 7.4.67-C t0 gives the extension callback's in-memory `temp_large_icon`
            // precedence over the original URI. The old product builder ignored this stock key,
            // so app-supplied extension artwork was lost even after restoring the Binder path.
            ExtensionNotificationContract.decodeTemporaryLargeIcon(metaInfo)
                ?: getLargeIcon(context, metaInfo, iconUri)
        } else {
            null
        }
        if (applyPayloadDecorations) {
            if (largeIcon != null) {
                notificationBuilder.setLargeIcon(largeIcon)
            }

            val subText = configuration.subText(null)
            buildExtraSubText(context, packageName, notificationBuilder, subText, color)
        }

        val previewNotification = ProgressStyleBuilder.buildNotification(context, notificationBuilder)
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
        val configuredFocusBundle = if (preliminaryFocusPlan.attachMiuiFocusExtras) {
            NotificationFocusPayloadSupport.buildConfiguredFocusBundle(
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
                keepNotificationVisible = islandOptions.showNotification,
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
        val focusPayloadSource = when {
            !rawConfiguredFocusParam.isNullOrBlank() -> "configured"
            generatedFocusBundle != null -> "generated"
            else -> "none"
        }
        logD(
            "focus payload plan pkg=$packageName id=$notificationId " +
                "source=$focusPayloadSource reason=${focusPlan.reason}",
        )

        val tag = MIPushNotificationPublishHelper.getNotificationTag(packageName)
        val nativeFeature = NativeNotificationFeatureBuilder.apply(
            context = context,
            builder = notificationBuilder,
            metaInfo = metaInfo,
            packageName = packageName,
            focusPlan = focusPlan,
            contentIntent = previewNotification.contentIntent,
            notificationKey = NativeNotificationFeatureBuilder.notificationKey(packageName, notificationId, tag, userId),
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
        // Stock 7.4.67-C t0.l applies top priority after style selection. Keep this after native
        // progress/message and focus translation because those product builders write HIGH.
        SweetNotificationCoordinator.applyIfEligible(
            context = context,
            packageName = packageName,
            notificationId = notificationId,
            metaInfo = metaInfo,
            builder = notificationBuilder,
            userId = userId,
        )
        // Stock 7.4.67-C t0 uses one local post timestamp for Notification.when and
        // mipush_org_when. The old product path sampled the clock again inside the coordinator,
        // which made the visible record and bounded lifecycle start at different instants.
        TopNotificationCoordinator.applyIfEligible(
            context,
            metaInfo,
            notificationBuilder,
            originalWhenMs = previewNotification.`when`,
        )
        if (isMockReplay) {
            applyMockReplayVisibility(notificationBuilder, packageName, notificationId)
        }

        // Stock 7.4.67-C sort.c/d observes removals through NotificationListenerService and never
        // replaces a notification's deleteIntent. The old focus/live-update receivers overwrote
        // caller cleanup callbacks, so preserve the builder's existing deleteIntent unchanged.
        if (shouldAutoCancelNotification(metaInfo, notificationBuilder, nativeFeature)) {
            notificationBuilder.setAutoCancel(true)
        }
        val notification = NativeNotificationFeatureBuilder.buildNotification(context, notificationBuilder, nativeFeature)
        val channel = getNotificationManagerEx().getNotificationChannel(packageName, notification.channelId)
        when {
            channel == null -> {
                logD(
                    "drop unavailable matched channel pkg=$packageName id=$notificationId " +
                        "channel=${notification.channelId} source=${if (isMockReplay) "mock_replay" else "server"}"
                )
                NativeNotificationFeatureBuilder.releaseMediaSession(packageName, notificationId, tag, userId)
                return NotifyResult.Failed
            }
            channel.importance == NotificationManager.IMPORTANCE_NONE -> {
                logD(
                    "drop disabled matched channel pkg=$packageName id=$notificationId " +
                        "channel=${notification.channelId} importance=${channel.importance} " +
                        "source=${if (isMockReplay) "mock_replay" else "server"}"
                )
                NativeNotificationFeatureBuilder.releaseMediaSession(packageName, notificationId, tag, userId)
                return NotifyResult.ChannelDisabled
            }
        }
        if (
            !NotificationContentSupport.hasMeaningfulVisibleContent(
                context = context,
                packageName = packageName,
                notification = notification,
                channelName = channel.name,
                channelDescription = channel.description,
            )
        ) {
            logD("drop contentless notification pkg=$packageName id=$notificationId channel=${notification.channelId}")
            NativeNotificationFeatureBuilder.releaseMediaSession(packageName, notificationId, tag, userId)
            return NotifyResult.Failed
        }
        val notificationToPost = if (focusPlan.allowIslandProxy &&
            generatedFocusBundle != null &&
            NotificationIslandProxySupport.sendGeneratedProxy(
                context = context,
                metaInfo = metaInfo,
                packageName = packageName,
                notificationId = notificationId,
                tag = tag,
                notification = notification,
                options = islandOptions,
                resolveUserId = ::resolveNotificationUserId,
            )
        ) {
            if (isMockReplay) {
                logD("keep mock replay original notification alerting pkg=$packageName id=$notificationId")
            } else {
                NotificationIslandProxySupport.quietGeneratedStatusBarNotification(notificationBuilder)
            }
            NativeNotificationFeatureBuilder.buildNotification(
                context,
                notificationBuilder,
                NativeNotificationFeatureBuilder.Result.NONE,
            )
        } else {
            notification
        }
        // Stock 7.4.67-C t$b.e clears/replaces the prior sweet timeout after the final build but
        // before any agent or NotificationManager publish attempt. Preserve that failure ordering.
        SweetNotificationCoordinator.onNotificationBuilt(
            context = context,
            packageName = packageName,
            notificationId = notificationId,
            metaInfo = metaInfo,
            notification = notificationToPost,
            userId = userId,
        )
        if (!islandOptions.showOriginalNotification) {
            Logger.withTag(TAG).d {
                "skip original notification post pkg=$packageName id=$notificationId " +
                    "tag=$tag showOriginalNotification=false"
            }
            PushRuntime.observeNotificationEvent(
                packageName,
                "notification_original_skipped",
                "NotificationController.publish",
            )
            NativeNotificationFeatureBuilder.releaseMediaSession(packageName, notificationId, tag, userId)
            return NotifyResult.SuppressedByPolicy
        }
        val postResult = TopNotificationCoordinator.postNotificationDetailed(
            context = context,
            packageName = packageName,
            tag = tag,
            notificationId = notificationId,
            messageId = metaInfo.id,
            notification = notificationToPost,
            userId = userId,
        )
        if (!postResult.posted) {
            Logger.withTag(TAG).w {
                "publish failed pkg=$packageName id=$notificationId tag=$tag channel=${notificationToPost.channelId} " +
                    "owner=${postResult.owner} reason=${postResult.reason}"
            }
            PushRuntime.observeNotificationEvent(packageName, "notification_publish_failed", "NotificationController.publish")
            NativeNotificationFeatureBuilder.releaseMediaSession(packageName, notificationId, tag, userId)
            return NotifyResult.Failed
        }
        if (postResult.owner == NotificationPostOwner.LOCAL_XMSF) {
            Logger.withTag(TAG).w {
                "publish used local XMSF fallback pkg=$packageName id=$notificationId tag=$tag " +
                    "channel=${notificationToPost.channelId} reason=${postResult.reason}"
            }
            PushRuntime.observeNotificationEvent(
                packageName,
                "notification_publish_local_fallback",
                "NotificationController.publish",
            )
        }
        SweetNotificationCoordinator.onNotificationPosted(
            context = context,
            packageName = packageName,
            notificationId = notificationId,
            notification = notificationToPost,
            userId = userId,
        )
        if (shouldPostMockReplayVisibleReceipt(isMockReplay, islandOptions)) {
            postMockReplayVisibleReceipt(
                context,
                packageName,
                notificationId,
                tag,
                notificationToPost,
                replaceOriginal = true,
                colorStatusBarIcon = islandOptions.colorStatusBarIcon,
            )
        }
        PushRuntime.observeNotificationEvent(packageName, "notification_publish_posted", "NotificationController.publish")
        return NotifyResult.Posted(notificationToPost)
    }

    private fun PushMetaInfo.isMockReplay(): Boolean =
        NotificationMockReplaySupport.isMockReplay(this)

    /** Keep normal and replay notifications on the same SystemUI owner-resolution path. */
    private fun addTargetPackageIdentity(extras: Bundle, packageName: String) {
        extras.putString("target_package", packageName)
        extras.putString("miui.targetPkg", packageName)
    }

    private fun applyMockReplayVisibility(
        notificationBuilder: NotificationCompat.Builder,
        packageName: String,
        notificationId: Int,
    ) = NotificationMockReplaySupport.applyVisibility(notificationBuilder, packageName, notificationId)

    internal fun shouldPostMockReplayVisibleReceipt(
        isMockReplay: Boolean,
        options: MiPushIslandOptions,
    ): Boolean = NotificationMockReplaySupport.shouldPostVisibleReceipt(isMockReplay, options)

    internal fun shouldAttachPayloadLargeIcon(
        isMockReplay: Boolean,
        colorStatusBarIcon: Boolean,
    ): Boolean = NotificationMockReplaySupport.shouldAttachPayloadLargeIcon(isMockReplay, colorStatusBarIcon)

    internal fun mockReplayReceiptNotificationId(packageName: String): Int =
        NotificationMockReplaySupport.receiptNotificationId(packageName)

    internal fun postMockReplayVisibleReceipt(
        context: Context,
        packageName: String,
        notificationId: Int,
        originalTag: String? = null,
        source: Notification,
        replaceOriginal: Boolean = false,
        colorStatusBarIcon: Boolean? = null,
    ): Boolean = NotificationMockReplaySupport.postVisibleReceipt(
        context = context,
        packageName = packageName,
        notificationId = notificationId,
        originalTag = originalTag,
        source = source,
        replaceOriginal = replaceOriginal,
        colorStatusBarIcon = colorStatusBarIcon,
        resolveUserId = ::resolveNotificationUserId,
        applyStatusBarIcon = ::applyStatusBarIcon,
    )

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
    fun getLargeIcon(context: Context, metaInfo: PushMetaInfo, iconUri: String?): Bitmap? =
        NotificationLargeIconSupport.getLargeIcon(context, metaInfo, iconUri)

    internal fun clearIconPackCache() {
        iconPackResolver.clearCache()
    }

    @JvmStatic
    fun roundLargeIconIfConfigured(metaInfo: PushMetaInfo, largeIcon: Bitmap): Bitmap =
        NotificationLargeIconSupport.roundLargeIconIfConfigured(metaInfo, largeIcon)

    @JvmStatic
    fun getBitmapFromUri(context: Context, iconUri: String?, maxDownloadBytes: Int): Bitmap? =
        NotificationLargeIconSupport.getBitmapFromUri(context, iconUri, maxDownloadBytes)

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
        val packageName = MIPushNotificationHelper.getTargetPackage(container)
        val userId = resolveNotificationUserId(context, packageName)
        MIPushNotificationStyleSupport.clearConversationHistory(packageName, notificationId, userId)
        val tag = MIPushNotificationPublishHelper.getNotificationTag(container)
        NativeNotificationFeatureBuilder.releaseMediaSession(packageName, notificationId, tag, userId)
        TopNotificationCoordinator.cancelNotification(
            context = context,
            packageName = packageName,
            tag = tag,
            notificationId = notificationId,
            userId = userId,
        )
        SweetNotificationCoordinator.cancelNotification(
            context = context,
            packageName = packageName,
            notificationId = notificationId,
            userId = userId,
        )
        NotificationIslandProxySupport.cancelGeneratedProxy(
            context = context,
            packageName = packageName,
            notificationId = notificationId,
            tag = tag,
            resolveUserId = ::resolveNotificationUserId,
        )
        FocusNotificationLifecycle.end(
            context = context,
            packageName = packageName,
            notificationId = notificationId,
            tag = tag,
            cancelNotification = true,
            userId = userId,
        )
        if (clearGroup) {
            getNotificationManagerEx().cancel(
                packageName,
                MIPushNotificationPublishHelper.getNotificationTag(container),
                notificationGroup?.let { ("GroupSummary" + packageName + it).hashCode() } ?: 0,
                userId = userId,
            )
        }
    }

    @JvmStatic
    fun getIconColor(ctx: Context, pkg: String): Int {
        return NotificationIconRenderingSupport.getIconColor(ctx, pkg)
    }

    @JvmStatic
    fun processIcon(context: Context, packageName: String, notificationBuilder: NotificationCompat.Builder): Int {
        return NotificationIconRenderingSupport.processIcon(context, packageName, notificationBuilder)
    }

    internal fun applyStatusBarIcon(
        context: Context,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder,
        colorStatusBarIcon: Boolean,
    ): Int {
        // Monochrome mode must not post multi-color brand BITMAP/RESOURCE logos as smallIcon.
        // HyperOS SRC_IN cannot desaturate multi-color pixels. Prefer a white-alpha silhouette
        // BITMAP from IconCache/raw app icon; the Material bell is last-resort only.
        val color = if (colorStatusBarIcon) {
            processIcon(context, packageName, notificationBuilder)
        } else {
            processMonochromeStatusBarIcon(context, packageName, notificationBuilder)
        }
        if (colorStatusBarIcon) {
            notificationBuilder.setColor(color)
            return color
        }
        notificationBuilder.setColor(Notification.COLOR_DEFAULT)
        return Notification.COLOR_DEFAULT
    }

    @SuppressLint("RestrictedApi")
    internal fun applyIconPackSmallIcon(
        context: Context,
        targetPackage: String,
        notificationBuilder: NotificationCompat.Builder,
        colorStatusBarIcon: Boolean,
        userId: Int? = null,
        resolver: IconPackResolver = iconPackResolver,
    ): ResolveResult? {
        if (targetPackage.isBlank()) return null

        // The configured directory is the existing product icon-pack source. ConfigCenter loads
        // <tree>/icon/*.json into IconConfigurations; use it before the optional protocol seam.
        // A non-null, enabled, decodable entry is the concrete "path exists" signal.
        val configuredIcon = runCatching {
            Global.iconConfigurations().get(targetPackage)
        }.getOrNull()
        val configuredBitmap = configuredIcon?.bitmap()
        if (configuredIcon?.isEnabled == true && configuredBitmap != null && !configuredBitmap.isRecycled) {
            runCatching {
                notificationBuilder.setSmallIcon(
                    IconCompat.createFromIcon(Icon.createWithBitmap(configuredBitmap)),
                )
                notificationBuilder.addExtras(
                    Bundle().apply {
                        putString(
                            ICON_PACK_SOURCE_IDENTITY_EXTRA,
                            thirdPartyPackSourceIdentity(targetPackage),
                        )
                    },
                )
                if (colorStatusBarIcon) {
                    val iconColor = configuredIcon.color()
                    if (iconColor != NotificationCompat.COLOR_DEFAULT) {
                        notificationBuilder.setColor(iconColor)
                    }
                }
                logI("applied configured icon pack target=$targetPackage")
            }.onFailure {
                logW("failed to apply configured icon pack target=$targetPackage", it)
            }.getOrNull()?.let { return null }
        }

        val result = runCatching {
            resolver.resolve(targetPackage, userId, context)
        }.getOrNull() ?: return null
        // Do not let a reused builder carry a prior notification's source identity into a
        // blocked/failed resolution.
        notificationBuilder.addExtras(Bundle().apply { putString(ICON_PACK_SOURCE_IDENTITY_EXTRA, null) })
        if (result is ResolveResult.Available) {
            // NotificationCompat carries this as IconCompat, but the framework Notification gets
            // the exact platform Icon.createWithBitmap source. No second bitmap is synthesized.
            val applied = runCatching {
                notificationBuilder.setSmallIcon(
                    IconCompat.createFromIcon(Icon.createWithBitmap(result.value.bitmap)),
                )
                // The marker audits the source identity; the bitmap remains on the framework
                // Notification.smallIcon and is the only header transport used by SystemUI.
                notificationBuilder.addExtras(
                    Bundle().apply {
                        putString(ICON_PACK_SOURCE_IDENTITY_EXTRA, result.value.sourceIdentity)
                    },
                )
                // iconColor is only the existing notification color metadata. Its absence never
                // invalidates the bitmap, and monochrome mode keeps its established default color.
                val iconColor = result.value.iconColor
                if (colorStatusBarIcon && iconColor != null) {
                    notificationBuilder.setColor(iconColor)
                }
                true
            }.getOrDefault(false)
            if (!applied) {
                notificationBuilder.addExtras(Bundle().apply {
                    putString(ICON_PACK_SOURCE_IDENTITY_EXTRA, null)
                })
                return ResolveResult.Unavailable(ResolveFailure.LOAD_FAILED)
            }
        }
        return result
    }

    /**
     * Build a monochrome-friendly small icon without using config/cache BITMAP brand artwork.
     * Returns the brand color only for callers that still want it when color mode is on; monochrome
     * callers discard it via [applyStatusBarIcon].
     */
    private fun processMonochromeStatusBarIcon(
        context: Context,
        packageName: String,
        notificationBuilder: NotificationCompat.Builder,
    ): Int {
        val color = getIconColor(context, packageName)
        val pkgContext = XMPushUtils.getPackageContext(
            context,
            packageName,
            Context.CONTEXT_IGNORE_SECURITY,
        )
        // Shade avatars stay on largeIcon. Status-bar smallIcon must already be monochrome pixels:
        // multi-color launcher RESOURCE logos stay full-color on HyperOS even with SRC_IN tint.
        if (pkgContext !== context) {
            val largeIconId = getIconId(context, packageName, NOTIFICATION_LARGE_ICON)
            if (largeIconId > 0) {
                notificationBuilder.setLargeIcon(
                    BitmapFactory.decodeResource(pkgContext.resources, largeIconId),
                )
            }
        }
        // Prefer package white-alpha silhouette. Never seed Material bell first — that seed became
        // the status-bar icon for WeWork/xinyi when IconCache missed.
        val whiteStatusBarIcon = Global.iconCache().getIconCache(
            context,
            packageName,
            object : io.github.magisk317.mipush.common.cache.IconCache.Converter<Bitmap, IconCompat> {
                override fun convert(ctx: Context, b: Bitmap): IconCompat = IconCompat.createWithBitmap(b)
            },
        )
        if (whiteStatusBarIcon != null) {
            notificationBuilder.setSmallIcon(whiteStatusBarIcon)
            return color
        }
        val rawIcon = Global.iconCache().getRawIconBitmap(context, packageName)
        if (rawIcon != null) {
            val white = ImgUtils.convertToTransparentAndWhite(rawIcon)
            notificationBuilder.setSmallIcon(IconCompat.createWithBitmap(white))
            return color
        }
        // Absolute last resort only (package icon unavailable).
        notificationBuilder.setSmallIcon(IconCompat.createWithResource(context, CommonR.drawable.ic_notifications_black_24dp))
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
        testMock(context, io.github.magisk317.mipush.notification.mock.MockNotificationKind.BIG_TEXT, packageName)
    }

    @JvmStatic
    fun testMock(
        context: Context,
        kind: io.github.magisk317.mipush.notification.mock.MockNotificationKind,
        packageName: String,
    ) = NotificationMockTestSupport.testMock(
        context = context,
        kind = kind,
        packageName = packageName,
        resolveUserId = ::resolveNotificationUserId,
        applyStatusBarIcon = ::applyStatusBarIcon,
    )

    internal fun resolveNotificationUserId(context: Context, packageName: String): Int {
        return runCatching {
            val uid = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getApplicationInfo(
                    packageName,
                    PackageManager.ApplicationInfoFlags.of(0),
                ).uid
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getApplicationInfo(packageName, 0).uid
            }
            require(uid >= 0) { "Invalid target package uid: $uid" }
            (uid.toLong() / PER_USER_RANGE).toInt()
        }.getOrElse { error("Unable to resolve notification user for $packageName: ${it.message}") }
    }


}
