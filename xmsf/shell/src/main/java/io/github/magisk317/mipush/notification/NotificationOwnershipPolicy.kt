package io.github.magisk317.mipush.notification

internal object NotificationOwnershipPolicy {
    fun canAccessUser(requestedUserId: Int, currentUserId: Int): Boolean =
        requestedUserId >= 0 && currentUserId >= 0 && requestedUserId == currentUserId

    /**
     * A channel ID is opaque to the framework. Once the caller has explicitly entered the
     * target-package fallback path, the ID must not be used as an ownership test: stock ROMs
     * legitimately use names such as `mipush|pkg|source` or `mipush_pkg_source`.
     */
    fun shouldUseLocalChannel(packageName: String, channelId: String?, hostPackageName: String): Boolean =
        packageName == hostPackageName || !channelId.isNullOrBlank()

    /** Group IDs are opaque for the same reason as channel IDs. */
    fun shouldUseLocalGroup(packageName: String, groupId: String?, hostPackageName: String): Boolean =
        packageName == hostPackageName || !groupId.isNullOrBlank()

    fun shouldUseLocalNotificationState(packageName: String, hostPackageName: String): Boolean =
        packageName == hostPackageName
}
