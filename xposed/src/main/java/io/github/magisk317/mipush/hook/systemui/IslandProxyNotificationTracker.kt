package io.github.magisk317.mipush.hook.systemui

import java.util.LinkedHashMap

internal object IslandProxyNotificationIds {
    fun fromStatusBarKey(
        key: String?,
        packageName: String,
        notificationId: Int,
        tag: String?,
    ): Int {
        val sourceKey = key ?: "$packageName:$notificationId:${tag.orEmpty()}"
        return "mipush_island:$sourceKey".hashCode()
    }
}

internal class IslandProxyPostTracker(
    private val ttlMs: Long,
    private val now: () -> Long = System::currentTimeMillis,
) {
    private val posts = LinkedHashMap<Int, Long>()

    fun shouldSkip(notificationId: Int): Boolean {
        val current = now()
        synchronized(posts) {
            pruneLocked(current)
            val previous = posts[notificationId]
            posts[notificationId] = current
            return previous != null && current - previous <= ttlMs
        }
    }

    private fun pruneLocked(current: Long) {
        val iterator = posts.entries.iterator()
        while (iterator.hasNext()) {
            if (current - iterator.next().value > ttlMs) {
                iterator.remove()
            }
        }
    }
}
