package io.github.magisk317.mipush.notification

internal object NotificationOwnershipPolicy {
    fun canAccessUser(requestedUserId: Int, currentUserId: Int): Boolean =
        requestedUserId.coerceAtLeast(0) == currentUserId.coerceAtLeast(0)

    fun shouldUseLocalChannel(packageName: String, channelId: String?, hostPackageName: String): Boolean =
        packageName == hostPackageName ||
            io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedChannelId(packageName, channelId)

    fun shouldUseLocalGroup(packageName: String, groupId: String?, hostPackageName: String): Boolean =
        packageName == hostPackageName ||
            io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedGroupId(packageName, groupId)

    fun shouldUseLocalNotificationState(packageName: String, hostPackageName: String): Boolean =
        packageName == hostPackageName
}
