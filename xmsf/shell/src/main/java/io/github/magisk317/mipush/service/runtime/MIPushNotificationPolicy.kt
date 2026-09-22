package io.github.magisk317.mipush.service.runtime

import com.xiaomi.xmpush.thrift.ActionType
import io.github.magisk317.mipush.utils.PackageConfig

internal object MIPushNotificationPolicy {
    private const val replayWindowMillis = 6 * 60 * 60 * 1000L

    fun shouldPublishNotification(
        action: ActionType?,
        passThrough: Int?,
        title: String?,
        description: String?,
    ): Boolean = when (action) {
        ActionType.SendMessage -> true
        ActionType.Notification -> passThrough == 0 &&
            (!title.isNullOrBlank() || !description.isNullOrBlank())
        else -> false
    }

    fun shouldSuppressForegroundNotification(
        isNotifyForeground: Boolean,
        isMiui: Boolean,
        isTargetForeground: Boolean,
    ): Boolean = isMiui && !isNotifyForeground && isTargetForeground

    fun shouldDispatchNonDisplayPayload(
        isRequest: Boolean,
        action: ActionType?,
    ): Boolean = !isRequest && when (action) {
        ActionType.Registration,
        ActionType.UnRegistration,
        ActionType.Command -> true
        else -> false
    }

    /**
     * A Notification container the display path cannot render (no metaInfo title/description, or
     * a pass-through shaped payload — e.g. Alipay pushsdk channels that ask XMSF to present a
     * message they already received) still belongs to its target app. Stock hands the raw bytes
     * over with MESSAGE_ARRIVED so the app renders its own surface; without that handoff the
     * payload is silently swallowed at the end of the dispatch chain.
     */
    fun shouldHandoffNonDisplayNotification(
        action: ActionType?,
        isMockReplay: Boolean,
        isBusinessMessage: Boolean,
    ): Boolean = action == ActionType.Notification && !isMockReplay && !isBusinessMessage

    fun shouldDropReplayNotification(
        action: ActionType?,
        hasMessageTimestamp: Boolean,
        messageTimestampMs: Long,
        sessionStartedAtMs: Long,
    ): Boolean = action == ActionType.SendMessage &&
        hasMessageTimestamp &&
        messageTimestampMs > 0L &&
        sessionStartedAtMs > 0L &&
        messageTimestampMs < sessionStartedAtMs - replayWindowMillis

    /**
     * Resolve the independent dispatch phases for a valid payload.
     *
     * Per-package configuration evaluation can fail (unreadable configuration, malformed rule
     * set, custom Set that throws on `contains`). Stock delivers the payload whenever the
     * payload itself is valid, so a failed evaluation must degrade to [NotificationDispatchPlan.NOTIFY_ONLY]
     * rather than dropping the notification: the target app still learns about the message.
     */
    fun resolveDispatchPlan(operations: Set<String>?): NotificationDispatchPlan {
        if (operations == null) return NotificationDispatchPlan.NOTIFY_ONLY
        return runCatching {
            NotificationDispatchPlan(
                wake = operations.contains(PackageConfig.OPERATION_WAKE),
                notify = !operations.contains(PackageConfig.OPERATION_IGNORE),
                open = operations.contains(PackageConfig.OPERATION_OPEN),
            )
        }.getOrDefault(NotificationDispatchPlan.NOTIFY_ONLY)
    }

    fun resolveStockGroup(
        targetPackage: String,
        sourceGroup: String?,
        disableDefault: Boolean,
        isMiui: Boolean,
    ): String? {
        val group = sourceGroup?.takeIf { it.isNotBlank() } ?: return null
        return if (!isMiui && disableDefault) group else targetPackage
    }

    fun buildStockMiuiIdentityExtras(
        targetPackage: String,
        messageId: String?,
        eventMessageType: Int,
    ): Map<String, String> = linkedMapOf<String, String>().apply {
        put("target_package", targetPackage)
        messageId?.takeIf(String::isNotEmpty)?.let { put("message_id", it) }
        put("eventMessageType", eventMessageType.toString())
    }
}
