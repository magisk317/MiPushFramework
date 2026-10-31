package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logW

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.core.app.NotificationCompat
import io.github.magisk317.mipush.common.notification.iconpack.IconPackResolver
import io.github.magisk317.xposed.logging.XLog
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.service.runtime.MIPushNotificationPublishHelper
import io.github.magisk317.mipush.service.runtime.getNotificationTag
import io.github.magisk317.mipush.service.runtime.MIPushNotificationStyleSupport
import io.github.magisk317.mipush.service.runtime.ExtensionNotificationContract
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.NotificationGroupHelper
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.stock.StockNotificationMetadataBridge
import io.github.magisk317.mipush.common.BuildConfig
import io.github.magisk317.mipush.common.notification.NotificationContentSupport
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.utils.ColorUtil
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.common.logging.StableLogTag

/**
 * Publish/notify/cancel facade for the push notification pipeline.
 *
 * Icon-pipeline seams live in NotificationControllerIconSeams.kt and the
 * mock-replay seams in NotificationControllerMockReplaySeams.kt, both as
 * same-package extension functions, so call sites keep the
 * `NotificationController.foo(...)` syntax.
 */
@StableLogTag
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
    private const val FOCUS_PARAM = "miui.focus.param"
    private const val FOCUS_PICS = "miui.focus.pics"
    private const val PIC_ICON = "miui.focus.pic_mipush_icon"
    private const val SYSTEM_UI_PACKAGE = "com.android.systemui"
    private const val ACTION_SHOW_ISLAND = "io.github.magisk317.mipush.action.SHOW_ISLAND"
    private const val EXTRA_ALLOW_ISLAND_PROXY = "mipush_island_allow_proxy"
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
                XLog.tagged(TAG).d {
                    "publish skipped unavailable matched channel pkg=$packageName id=$notificationId " +
                        "channel=$channelId source=${if (metaInfo.isMockReplay()) "mock_replay" else "server"}"
                }
                emit(result = "skip", reason = "matched_channel_unavailable")
                return PublishResult.Failed
            }
            channel.importance == NotificationManager.IMPORTANCE_NONE -> {
                XLog.tagged(TAG).d {
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
            XLog.tagged(TAG).d { "publish skipped pkg=$packageName id=$notificationId (contentless, channel, or publish issue)" }
            emit(result = "skip", reason = "notify_null")
            return PublishResult.Failed
        }
        XLog.tagged(TAG).d {
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
        // Whether the dynamic-island proxy actually assumed responsibility for displaying this
        // notification. This captures the exact combination that previously decided the proxy
        // takeover so the original-notification suppression gate (below) only fires when the proxy
        // truly took over. If the proxy did NOT take over (island disabled, float mode off, focus
        // authorization lost, payload build failed, or sendGeneratedProxy returned false) the
        // original notification MUST remain posted, otherwise the user receives zero notifications.
        val islandProxyTookOver = focusPlan.allowIslandProxy &&
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
        val notificationToPost = if (islandProxyTookOver) {
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
        // Only suppress the original notification when the user opted out of showing it AND the
        // island proxy actually took over its display. When islandProxyTookOver is false the proxy
        // is not showing anything on the user's behalf, so dropping the original would leave them
        // with no notification at all (the silent-loss regression). See shouldSuppressOriginalByIslandProxy.
        if (shouldSuppressOriginalByIslandProxy(islandOptions.showOriginalNotification, islandProxyTookOver)) {
            XLog.tagged(TAG).d {
                "skip original notification post pkg=$packageName id=$notificationId " +
                    "tag=$tag showOriginalNotification=false islandProxyTookOver=$islandProxyTookOver"
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
            XLog.tagged(TAG).w {
                "publish failed pkg=$packageName id=$notificationId tag=$tag channel=${notificationToPost.channelId} " +
                    "owner=${postResult.owner} reason=${postResult.reason}"
            }
            PushRuntime.observeNotificationEvent(packageName, "notification_publish_failed", "NotificationController.publish")
            NativeNotificationFeatureBuilder.releaseMediaSession(packageName, notificationId, tag, userId)
            return NotifyResult.Failed
        }
        if (postResult.owner == NotificationPostOwner.LOCAL_XMSF) {
            XLog.tagged(TAG).w {
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
        if (shouldPostMockReplayVisibleReceipt(isMockReplay, islandOptions, islandProxyTookOver)) {
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

    /** Cancels one (packageName, tag, id) slot with every coordinator that tracks publish identity. */
    private fun cancelNotificationSlot(
        context: Context,
        packageName: String,
        tag: String?,
        notificationId: Int,
        userId: Int,
    ) {
        MIPushNotificationStyleSupport.clearConversationHistory(packageName, notificationId, userId)
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
        val tag = MIPushNotificationPublishHelper.getNotificationTag(container)
        cancelNotificationSlot(context, packageName, tag, notificationId, userId)
        // [PRODUCT-OWNED DEVIATION — see FocusOrderIdentityPolicy] A live-session card posts under
        // the order-derived id, so a cancel container that repeats the focus param must also
        // target that id; stock cancels keyed on the notifyId alone. Recheck stock cancel
        // semantics on XMSF upgrades before retiring this branch.
        val orderNotificationId = FocusOrderIdentityPolicy.resolveNotificationId(packageName, container.metaInfo)
        if (orderNotificationId != null && orderNotificationId != notificationId) {
            cancelNotificationSlot(context, packageName, tag, orderNotificationId, userId)
        }
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
