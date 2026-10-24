package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.push.hook.ExplicitHookBridge
import io.github.magisk317.mipush.push.pipeline.MessageIdentity
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.notification.FocusOrderIdentityPolicy
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.notification.AndroidWGroupStrategy
import io.github.magisk317.mipush.notification.VoipNotificationHelper
import io.github.magisk317.mipush.notification.SweetNotificationCoordinator
import io.github.magisk317.mipush.manager.application.MockReplayOutcome

/**
 * Notification construction and publish-identity seams of
 * [MIPushNotificationPublishHelper], split out of the facade class so it keeps
 * only the entry gating, policy dispatch and configuration loading.
 *
 * Same-package extension functions on the companion object: call sites keep the
 * plain `MIPushNotificationPublishHelper.foo(...)` syntax (an import is required
 * outside this package), and inside this file the implicit receiver carries the
 * generic logD/logE/logI/logW extensions. The facade's transient state (session
 * timestamp, dispatcher, wake throttle, dedup windows) stays in the companion
 * and is not touched here.
 */

internal fun MIPushNotificationPublishHelper.Companion.doNotifyPushMessage(
    context: Context,
    container: XmPushActionContainer,
    decryptedContent: ByteArray,
    sendMessage: XmPushActionSendMessage?,
    dispatchMessageArrived: Boolean = false,
): MockReplayOutcome {
    val metaInfo = container.metaInfo
    val messageId = MessageIdentity.fromContainer(container)
    val isMockReplay = MockMessageRegistry.isMarked(container)
    if (metaInfo == null) {
        logW("doNotifyPushMessage: metaInfo is null, skip notification pkg=${container.packageName} messageId=$messageId")
        return finishNotificationDispatch(
            context,
            container,
            decryptedContent,
            MockReplayOutcome.Failed,
            dispatchMessageArrived,
        )
    }
    val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
    val targetUserId = NotificationController.resolveNotificationUserId(context, targetPackage)
    val isMiui = MIUIUtils.isMIUI()
    if (shouldSuppressForegroundNotification(
            extra = metaInfo.extra,
            isMiui = isMiui,
            isTargetForeground = isMiui &&
                MIPushNotificationHelper.isApplicationForeground(context, container.packageName),
        )
    ) {
        // Stock 7.4.67-C i0 skips t0 entirely on MIUI when notify_foreground is not 1
        // and the target app is foreground. The old product path applied this condition
        // only to MESSAGE_ARRIVED, so it still posted a notification the server suppressed.
        logI("skip foreground notification pkg=$targetPackage messageId=$messageId")
        PushRuntime.observeNotificationEvent(
            packageName = targetPackage,
            action = "foreground_notification_suppressed",
            source = "MIPushNotificationPublishHelper.doNotifyPushMessage",
        )
        return finishNotificationDispatch(
            context,
            container,
            decryptedContent,
            MockReplayOutcome.Dispatched,
            dispatchMessageArrived,
        )
    }
    val notificationId = getNotificationId(container)
    logD(
        "doNotifyPushMessage pkg=$targetPackage messageId=$messageId " +
            "notificationId=$notificationId mockReplay=$isMockReplay " +
            "messageTs=${metaInfo.messageTs} notifyId=${metaInfo.notifyId}",
    )
    // Stock 7.4.67-C t0 invokes sweet reminder filtering before its VoIP sequence filter.
    // The retained 3.7.9 helper has no remind_status lifecycle, so keep the newer behavior
    // in the product dispatch path instead of modifying pinned SDK sources.
    if (SweetNotificationCoordinator.shouldSuppress(
            context = context,
            packageName = targetPackage,
            notificationId = notificationId,
            metaInfo = metaInfo,
            userId = targetUserId,
        )
    ) {
        logD("skip repeated sweet notification pkg=$targetPackage messageId=$messageId")
        PushRuntime.observeNotificationEvent(
            packageName = targetPackage,
            action = "sweet_notification_drop",
            source = "MIPushNotificationPublishHelper.doNotifyPushMessage",
        )
        return finishNotificationDispatch(
            context,
            container,
            decryptedContent,
            MockReplayOutcome.Failed,
            dispatchMessageArrived,
        )
    }
    if (VoipNotificationHelper.shouldDropStale(metaInfo, targetPackage, targetUserId)) {
        logD("skip stale voip notification pkg=$targetPackage messageId=$messageId")
        PushRuntime.observeNotificationEvent(
            packageName = targetPackage,
            action = "voip_sequence_drop",
            source = "MIPushNotificationPublishHelper.doNotifyPushMessage"
        )
        return finishNotificationDispatch(
            context,
            container,
            decryptedContent,
            MockReplayOutcome.Failed,
            dispatchMessageArrived,
        )
    }
    if (VoipNotificationHelper.isVoipEndEvent(metaInfo)) {
        logD("cancel voip notification pkg=$targetPackage messageId=$messageId notificationId=$notificationId")
        NotificationController.cancel(context, container, notificationId, null, clearGroup = false)
        PushRuntime.observeNotificationEvent(
            packageName = targetPackage,
            action = "voip_cancel",
            source = "MIPushNotificationPublishHelper.doNotifyPushMessage"
        )
        return finishNotificationDispatch(
            context,
            container,
            decryptedContent,
            MockReplayOutcome.Dispatched,
            dispatchMessageArrived,
        )
    }
    // Stock 7.4.67-C i0/p0 intercepts an eligible hyper_type=1 notification after
    // duplicate/profile checks but before t0 builds the ordinary notification. The old
    // product path posted immediately, bypassing the target app's extension service.
    if (ExtensionNotificationCoordinator.handleIfEligible(
            context = context,
            container = container,
            payload = decryptedContent,
            publish = { resolvedContainer, resolvedPayload ->
                val resolvedSendMessage = if (resolvedContainer === container) {
                    sendMessage
                } else {
                    resolveSendMessage(context, resolvedContainer)
                }
                val outcome = postNotification(
                    context,
                    resolvedContainer,
                    resolvedPayload,
                    resolvedSendMessage,
                )
                // Stock 7.4.67-C returns from the original i0 branch, then invokes i0.f
                // from ExtensionMessageHandleCenter after callback/fallback. The first
                // implementation let the outer scope send MESSAGE_ARRIVED immediately
                // with the old payload, including when the app suppressed notification.
                finishNotificationDispatch(
                    context,
                    resolvedContainer,
                    resolvedPayload,
                    outcome,
                    dispatchMessageArrived,
                )
            },
        )
    ) {
        PushRuntime.observeNotificationEvent(
            packageName = targetPackage,
            action = "extension_notification_intercepted",
            source = "MIPushNotificationPublishHelper.doNotifyPushMessage",
        )
        return MockReplayOutcome.Dispatched
    }
    // Stock 7.4.67-C t0 publishes without consulting com.xiaomi.push.sort; that filter is
    // owned by notification collection. The old pre-publish check turned collection state
    // into a user-visible delivery block, so publishing must remain independent here.
    return finishNotificationDispatch(
        context,
        container,
        decryptedContent,
        postNotification(context, container, decryptedContent, sendMessage),
        dispatchMessageArrived,
    )
}

private fun MIPushNotificationPublishHelper.Companion.finishNotificationDispatch(
    context: Context,
    container: XmPushActionContainer,
    decryptedContent: ByteArray,
    outcome: MockReplayOutcome,
    dispatchMessageArrived: Boolean,
): MockReplayOutcome {
    if (dispatchMessageArrived) {
        dispatchMessageArrivedIfNeeded(context, container, decryptedContent)
    }
    return outcome
}

internal fun MIPushNotificationPublishHelper.Companion.mapPublishResult(
    result: NotificationController.PublishResult,
): MockReplayOutcome = when (result) {
    NotificationController.PublishResult.Posted -> MockReplayOutcome.Posted
    NotificationController.PublishResult.ChannelDisabled -> MockReplayOutcome.FailedChannelDisabled
    NotificationController.PublishResult.SuppressedByPolicy -> MockReplayOutcome.Dispatched
    NotificationController.PublishResult.Failed -> MockReplayOutcome.Failed
}

private fun MIPushNotificationPublishHelper.Companion.postNotification(
    context: Context,
    container: XmPushActionContainer,
    decryptedContent: ByteArray,
    sendMessage: XmPushActionSendMessage?,
): MockReplayOutcome {
    val metaInfo = container.metaInfo ?: return MockReplayOutcome.Failed
    val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
    val messageId = MessageIdentity.fromContainer(container)
    val notificationId = getNotificationId(container)
    val result = MIPushNotificationPresentationSupport.getNotificationFor(
        context,
        container,
        decryptedContent,
        notificationId,
        sendMessage,
    )
    logD(
        "doNotifyPushMessage publish start pkg=$targetPackage action=${container.action} " +
            "messageId=$messageId notificationId=${result.notificationId}"
    )
    return mapPublishResult(
        NotificationController.publish(
            context,
            metaInfo,
            result.notificationId,
            targetPackage,
            result.notificationBuilder,
        ),
    )
}

fun MIPushNotificationPublishHelper.Companion.getNotificationId(container: XmPushActionContainer): Int {
    val packageName = MIPushNotificationHelper.getTargetPackage(container)
    val metaInfo = container.metaInfo
    val messageId = MessageIdentity.fromContainer(container)
    val isMockReplay = MockMessageRegistry.isMarked(container)
    if (isMockReplay && metaInfo != null) {
        val sourceId = metaInfo.extra?.get(MockMessageRegistry.EXTRA_MOCK_REPLAY_SOURCE_ID)
            ?.takeIf { it.isNotBlank() }
            ?: messageId?.takeIf { it.isNotBlank() }
            ?: metaInfo.id?.takeIf { it.isNotBlank() }
            ?: metaInfo.notifyId.toString()
        return "${packageName}_mock_replay:$sourceId".hashCode()
    }
    // [PRODUCT-OWNED DEVIATION — see FocusOrderIdentityPolicy] A live-session payload keys on
    // its order, not the per-message notifyId; recheck stock getNotificationId on XMSF
    // upgrades before retiring this branch.
    val focusOrderId = FocusOrderIdentityPolicy.resolveNotificationId(packageName, metaInfo)
    if (focusOrderId != null) {
        logD(
            "getNotificationId pkg=$packageName focusOrder=true messageId=$messageId " +
                "notifyId=${metaInfo?.notifyId} result=$focusOrderId"
        )
        return focusOrderId
    }
    val notifyId = metaInfo?.notifyId ?: 0
    val result = (packageName.hashCode() / 10) * 10 + notifyId
    // A focus payload that produces no order key is the failure mode this policy regresses
    // into, and from here it is indistinguishable from an ordinary notification: the card
    // just stops merging. Flag it so the next time a vendor moves its session fields it is
    // one grep away rather than another six days of stacked islands later.
    val liveSessionUnresolved = FocusOrderIdentityPolicy.hasFocusParam(metaInfo)
    logD(
        "getNotificationId pkg=$packageName notifyId=$notifyId mockReplay=$isMockReplay " +
            "messageId=$messageId liveSessionUnresolved=$liveSessionUnresolved result=$result"
    )
    return result
}

fun MIPushNotificationPublishHelper.Companion.getNotificationTag(packageName: String): String? = null

fun MIPushNotificationPublishHelper.Companion.getNotificationTag(container: XmPushActionContainer): String? {
    return getNotificationTag(container.packageName)
}

internal fun MIPushNotificationPublishHelper.Companion.resolveStockGroup(
    targetPackage: String,
    sourceGroup: String?,
    disableDefault: Boolean,
    isMiui: Boolean,
): String? = MIPushNotificationPolicy.resolveStockGroup(
    targetPackage = targetPackage,
    sourceGroup = sourceGroup,
    disableDefault = disableDefault,
    isMiui = isMiui,
)

internal fun MIPushNotificationPublishHelper.Companion.shouldSkipForceGroup(sourceGroup: String?, strategy: Int): Boolean {
    return AndroidWGroupStrategy.shouldSkipForceGroup(sourceGroup, strategy)
}

internal fun MIPushNotificationPublishHelper.Companion.buildStockMiuiIdentityExtras(
    container: XmPushActionContainer,
    targetPackage: String = MIPushNotificationHelper.getTargetPackage(container),
): Map<String, String> {
    val metaInfo = container.metaInfo
    val messageType = when {
        MIPushNotificationHelper.isNormalNotificationMessage(container) -> 1000
        MIPushNotificationHelper.isBusinessMessage(container) -> 3000
        else -> -1
    }
    return buildStockMiuiIdentityExtras(targetPackage, metaInfo?.id, messageType)
}

internal fun MIPushNotificationPublishHelper.Companion.buildStockMiuiIdentityExtras(
    targetPackage: String,
    messageId: String?,
    eventMessageType: Int,
): Map<String, String> = MIPushNotificationPolicy.buildStockMiuiIdentityExtras(
    targetPackage = targetPackage,
    messageId = messageId,
    eventMessageType = eventMessageType,
)

private fun MIPushNotificationPublishHelper.Companion.getSourceGroup(metaInfo: PushMetaInfo): String? {
    return XMPushUtils.getConfiguration(metaInfo)
        .notificationGroup(null)
        ?.takeIf { it.isNotBlank() }
}

fun MIPushNotificationPublishHelper.Companion.buildTargetIntentWithoutExtras(pkg: String, metaInfo: PushMetaInfo): Intent {
    val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE)
        .addCategory(metaInfo.notifyId.toString())
        .setClassName(pkg, CLASS_NAME_PUSH_MESSAGE_HANDLER)
    ExplicitHookBridge.onBuildIntent(intent, "MIPushNotificationPublishHelper.buildTargetIntentWithoutExtras")
    return intent
}

fun MIPushNotificationPublishHelper.Companion.getSdkIntent(context: Context, container: XmPushActionContainer): Intent? {
    return MIPushNotificationIntentSupport.getSdkIntent(context, container)
}
