package io.github.magisk317.mipush.hook.systemui

import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import java.util.LinkedHashMap

internal object IslandProxyNotificationIds {
    fun fromPackage(packageName: String?): Int {
        val key = packageName?.takeIf { it.isNotBlank() }
            ?: IslandDispatchContract.SYSTEM_UI_PACKAGE
        return "mipush_island:$key".hashCode()
    }

    @Suppress("UNUSED_PARAMETER")
    fun fromStatusBarKey(
        key: String?,
        packageName: String,
        notificationId: Int,
        tag: String?,
    ): Int = fromPackage(packageName)
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
