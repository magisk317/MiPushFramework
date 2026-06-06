package io.github.magisk317.mipush.notification

internal object IslandProxyNotificationId {
    @Suppress("UNUSED_PARAMETER")
    fun fromPackage(packageName: String, notificationId: Int, tag: String?): Int {
        return "mipush_island:$packageName".hashCode()
    }
}
