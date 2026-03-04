package com.magisk317.push.pipeline

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.xmpush.thrift.XmPushActionContainer

object MockMessageRegistry {
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = "MockMessageRegistry")
    }
    private const val MARK_TTL_MS = 30_000L
    private val lock = Any()
    private val markedMessageIds = LinkedHashMap<String, Long>()

    @JvmStatic
    fun mark(container: XmPushActionContainer?) {
        val id = MessageIdentity.fromContainer(container) ?: return
        markMessageId(id)
    }

    @JvmStatic
    fun markMessageId(messageId: String?) {
        if (messageId.isNullOrBlank()) return
        val now = System.currentTimeMillis()
        synchronized(lock) {
            pruneExpiredLocked(now)
            markedMessageIds[messageId] = now
        }
        logger.d("marked mock message id=$messageId ttlMs=$MARK_TTL_MS")
    }

    @JvmStatic
    fun isMarked(container: XmPushActionContainer?): Boolean {
        val id = MessageIdentity.fromContainer(container) ?: return false
        return isMarked(id)
    }

    @JvmStatic
    fun isMarked(messageId: String?): Boolean {
        if (messageId.isNullOrBlank()) return false
        val now = System.currentTimeMillis()
        synchronized(lock) {
            pruneExpiredLocked(now)
            val ts = markedMessageIds[messageId] ?: return false
            if ((now - ts) > MARK_TTL_MS) {
                markedMessageIds.remove(messageId)
                return false
            }
            return true
        }
    }

    @JvmStatic
    fun clear(messageId: String?) {
        if (messageId.isNullOrBlank()) return
        synchronized(lock) {
            markedMessageIds.remove(messageId)
        }
    }

    @JvmStatic
    fun clearAllForTests() {
        synchronized(lock) {
            markedMessageIds.clear()
        }
    }

    private fun pruneExpiredLocked(now: Long) {
        val iterator = markedMessageIds.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((now - entry.value) > MARK_TTL_MS) {
                iterator.remove()
            }
        }
    }
}
