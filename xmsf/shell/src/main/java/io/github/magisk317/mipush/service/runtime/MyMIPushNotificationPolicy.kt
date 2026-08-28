package io.github.magisk317.mipush.service.runtime

import com.xiaomi.xmpush.thrift.ActionType

internal object MyMIPushNotificationPolicy {
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
