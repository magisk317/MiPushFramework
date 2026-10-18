package io.github.magisk317.mipush.notification

import android.app.Notification
import android.content.Context
import android.os.Bundle
import androidx.core.app.NotificationCompat
import com.xiaomi.xmpush.thrift.PushMetaInfo

/**
 * Mock-replay seams of [NotificationController], split out of the facade
 * object so the controller keeps only the publish/notify/cancel flow.
 *
 * Same-package extension functions: call sites keep the plain
 * `NotificationController.foo(...)` syntax (an import is required outside
 * this package). The real logic lives in [NotificationMockReplaySupport];
 * these are the controller-facing decision wrappers plus the visibility
 * helpers that stay on the builder.
 */

internal fun PushMetaInfo.isMockReplay(): Boolean =
    NotificationMockReplaySupport.isMockReplay(this)

/** Keep normal and replay notifications on the same SystemUI owner-resolution path. */
internal fun NotificationController.addTargetPackageIdentity(extras: Bundle, packageName: String) {
    extras.putString("target_package", packageName)
    extras.putString("miui.targetPkg", packageName)
}

internal fun NotificationController.applyMockReplayVisibility(
    notificationBuilder: NotificationCompat.Builder,
    packageName: String,
    notificationId: Int,
) = NotificationMockReplaySupport.applyVisibility(notificationBuilder, packageName, notificationId)

/**
 * Whether a visible mock-replay receipt should be posted.
 *
 * Delegates to [NotificationMockReplaySupport.shouldPostVisibleReceipt], threading through
 * [islandProxyTookOver] so the receipt is posted whenever the original notification might be
 * invisible (suppressed by opt-out, or replaced by the island proxy).
 */
internal fun NotificationController.shouldPostMockReplayVisibleReceipt(
    isMockReplay: Boolean,
    options: MiPushIslandOptions,
    islandProxyTookOver: Boolean,
): Boolean = NotificationMockReplaySupport.shouldPostVisibleReceipt(isMockReplay, options, islandProxyTookOver)

/**
 * Decides whether the original notification should be suppressed because the dynamic-island
 * proxy took over its display.
 *
 * The original is dropped only when BOTH conditions hold:
 *  - the user opted out of showing the original notification ([showOriginalNotification] == false), and
 *  - the island proxy actually assumed responsibility for displaying it ([islandProxyTookOver] == true).
 *
 * If the proxy did not take over (island disabled, float mode off, focus authorization lost,
 * payload build failed, or [sendGeneratedProxy] returned false) the original MUST remain posted,
 * otherwise the user receives zero notifications. This is the core fix for the
 * "island off + show-original off -> fully silent" regression.
 */
internal fun NotificationController.shouldSuppressOriginalByIslandProxy(
    showOriginalNotification: Boolean,
    islandProxyTookOver: Boolean,
): Boolean = !showOriginalNotification && islandProxyTookOver

internal fun NotificationController.shouldAttachPayloadLargeIcon(
    isMockReplay: Boolean,
    colorStatusBarIcon: Boolean,
): Boolean = NotificationMockReplaySupport.shouldAttachPayloadLargeIcon(isMockReplay, colorStatusBarIcon)

internal fun NotificationController.mockReplayReceiptNotificationId(packageName: String): Int =
    NotificationMockReplaySupport.receiptNotificationId(packageName)

internal fun NotificationController.postMockReplayVisibleReceipt(
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
    resolveUserId = { ctx, pkg -> resolveNotificationUserId(ctx, pkg) },
    applyStatusBarIcon = { ctx, pkg, builder, colorIcon ->
        applyStatusBarIcon(ctx, pkg, builder, colorIcon)
    },
)

internal fun NotificationController.shouldAutoCancelNotification(
    metaInfo: PushMetaInfo,
    notificationBuilder: NotificationCompat.Builder,
    nativeFeature: NativeNotificationFeatureBuilder.Result = NativeNotificationFeatureBuilder.Result.NONE,
): Boolean {
    return !VoipNotificationHelper.isVoipNotification(metaInfo) &&
        !nativeFeature.preventsAutoCancel &&
        !ProgressStyleBuilder.isLiveUpdate(notificationBuilder)
}
