package io.github.magisk317.mipush.hook.systemui

import io.github.magisk317.mipush.hook.island.IslandDispatchContract
import java.util.LinkedHashMap

internal object IslandProxyNotificationIds {
    fun fromPackage(packageName: String?, userId: Int): Int {
        val key = packageName?.takeIf { it.isNotBlank() }
            ?: IslandDispatchContract.SYSTEM_UI_PACKAGE
        return "mipush_island:$userId:$key".hashCode()
    }

    fun fromStatusBarKey(
        key: String?,
        packageName: String,
        notificationId: Int,
        tag: String?,
        userId: Int,
    ): Int = fromPackage(packageName, userId)
}

internal object IslandProxySourceKeys {
    fun fromStatusBarKey(
        key: String?,
        packageName: String?,
        notificationId: Int,
        tag: String?,
        userId: Int,
    ): String {
        key?.takeIf { it.isNotBlank() }?.let { return "$userId|$it" }
        val pkg = packageName?.takeIf { it.isNotBlank() }
            ?: IslandDispatchContract.SYSTEM_UI_PACKAGE
        return "$userId|$pkg#$notificationId#${tag.orEmpty()}"
    }
}

internal object IslandProxyDedupKeys {
    fun fromStatusBarKey(
        key: String?,
        packageName: String?,
        notificationId: Int,
        tag: String?,
        userId: Int,
    ): Int {
        val identity = key?.takeIf { it.isNotBlank() }
            ?: "${packageName.orEmpty()}#$notificationId#${tag.orEmpty()}"
        return "$userId|$identity".hashCode()
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

internal class IslandProxyOwnershipTracker(
    private val maxTrackedSources: Int,
) {
    private val sourceToProxy = LinkedHashMap<String, Int>()
    private val latestSourceByProxy = LinkedHashMap<Int, String>()

    @Synchronized
    fun record(sourceKey: String, proxyId: Int) {
        if (sourceToProxy.size >= maxTrackedSources && sourceKey !in sourceToProxy) {
            sourceToProxy.clear()
            latestSourceByProxy.clear()
        }
        sourceToProxy[sourceKey] = proxyId
        latestSourceByProxy[proxyId] = sourceKey
    }

    @Synchronized
    fun removeAndResolveCancellation(sourceKey: String): Int? {
        val proxyId = sourceToProxy.remove(sourceKey) ?: return null
        if (latestSourceByProxy[proxyId] != sourceKey) return null
        latestSourceByProxy.remove(proxyId)
        return proxyId
    }
}
