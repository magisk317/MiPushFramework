package io.github.magisk317.mipush.common.notification

data class NotificationAvailabilityRequest(
    val packageName: String?,
    val metaInfoExtra: Map<String, String>,
)

/** Runtime-owned notification policy queried by data/runtime consumers. */
fun interface NotificationAvailabilityReader {
    fun isNotificationDisabled(request: NotificationAvailabilityRequest): Boolean
}
