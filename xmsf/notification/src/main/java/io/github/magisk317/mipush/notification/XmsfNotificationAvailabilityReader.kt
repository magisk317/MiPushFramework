package io.github.magisk317.mipush.notification

import android.content.Context
import io.github.magisk317.mipush.common.notification.NotificationAvailabilityReader
import io.github.magisk317.mipush.common.notification.NotificationAvailabilityRequest
import io.github.magisk317.mipush.common.utils.Utils
import java.util.LinkedHashMap

internal class NotificationAvailabilityCache(
    private val maxEntries: Int = 256,
    private val ttlMs: Long = 3_000L,
) {
    internal data class Key(
        val userId: Int,
        val packageName: String,
        val channelId: String,
    )

    private data class Entry(val disabled: Boolean, val storedAtMs: Long)

    private val lock = Any()
    private val entries = object : LinkedHashMap<Key, Entry>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Key, Entry>?): Boolean =
            size > maxEntries
    }

    fun getOrLoad(key: Key, nowMs: Long, loader: () -> Boolean): Boolean {
        synchronized(lock) {
            entries[key]?.takeIf { nowMs - it.storedAtMs <= ttlMs }?.let { return it.disabled }
            entries.remove(key)
        }
        val loaded = loader()
        synchronized(lock) {
            entries[key] = Entry(loaded, nowMs)
        }
        return loaded
    }
}

/** XMSF adapter for notification availability; callers do not depend on channel APIs. */
class XmsfNotificationAvailabilityReader(
    private val context: Context,
) : NotificationAvailabilityReader {
    private val availabilityCache = NotificationAvailabilityCache()

    override fun isNotificationDisabled(request: NotificationAvailabilityRequest): Boolean {
        val packageName = request.packageName ?: return true
        val metaInfo = com.xiaomi.xmpush.thrift.PushMetaInfo().apply {
            extra = request.metaInfoExtra.toMutableMap()
        }
        if (!Utils.isAppInstalled(packageName)) return true
        // Event/status reads must not provision notification groups or channels. A missing
        // channel is treated as unknown/available here; the publish path owns provisioning.
        val channelId = NotificationAvailabilityShellBridge.findExistingChannelId(
                context,
                metaInfo,
                packageName,
            ) ?: return false
        val key = NotificationAvailabilityCache.Key(
            userId = Utils.requireValidUserId(Utils.myUserId()),
            packageName = packageName,
            channelId = channelId,
        )
        return availabilityCache.getOrLoad(
            key = key,
            nowMs = android.os.SystemClock.elapsedRealtime(),
        ) {
            !NotificationAvailabilityShellBridge.isNotificationChannelEnabled(packageName, channelId)
        }
    }
}
