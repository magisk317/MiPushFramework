package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.common.utils.Utils

internal object IslandProxyNotificationId {
    fun fromPackage(
        packageName: String,
        notificationId: Int,
        tag: String?,
        userId: Int = Utils.myUserId().coerceAtLeast(0),
    ): Int {
        return "mipush_island:$userId:$packageName".hashCode()
    }
}
