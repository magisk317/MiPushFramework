package io.github.magisk317.mipush.notification

object IslandProxyNotificationId {
    fun fromPackage(
        packageName: String,
        notificationId: Int,
        tag: String?,
        userId: Int,
    ): Int {
        return "mipush_island:$userId:$packageName".hashCode()
    }
}
