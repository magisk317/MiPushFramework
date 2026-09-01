package io.github.magisk317.mipush.notification

internal object NotificationOwnershipPolicy {
    fun canAccessUser(requestedUserId: Int, currentUserId: Int): Boolean =
        requestedUserId >= 0 && currentUserId >= 0 && requestedUserId == currentUserId

    fun shouldUseLocalChannel(packageName: String, channelId: String?, hostPackageName: String): Boolean =
        packageName == hostPackageName ||
            io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedChannelId(packageName, channelId)

    fun shouldUseLocalGroup(packageName: String, groupId: String?, hostPackageName: String): Boolean =
        packageName == hostPackageName ||
            io.github.magisk317.mipush.common.utils.NotificationUtils.isMiPushManagedGroupId(packageName, groupId)

    fun shouldUseLocalNotificationState(packageName: String, hostPackageName: String): Boolean =
        packageName == hostPackageName
}
