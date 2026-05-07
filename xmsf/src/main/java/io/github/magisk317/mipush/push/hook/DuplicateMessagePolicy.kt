package io.github.magisk317.mipush.push.hook

import java.util.LinkedHashMap

/**
 * Message duplicate policy based on messageId + time window.
 *
 * checkAndMark(messageId):
 * - false -> first seen in window
 * - true  -> duplicated within window
 */
object DuplicateMessagePolicy {
    private const val DEDUP_WINDOW_MS = 60_000L
    private val lock = Any()
    private val seen = LinkedHashMap<String, Long>()

    @JvmStatic
    fun checkAndMark(messageId: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        if (messageId.isNullOrBlank()) return false
        synchronized(lock) {
            pruneExpiredLocked(nowMs)
            val previous = seen[messageId]
            val duplicated = previous != null && (nowMs - previous) <= DEDUP_WINDOW_MS
            seen[messageId] = nowMs
            return duplicated
        }
    }

    @JvmStatic
    fun clearAllForTests() {
        synchronized(lock) {
            seen.clear()
        }
    }

    private fun pruneExpiredLocked(nowMs: Long) {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((nowMs - entry.value) > DEDUP_WINDOW_MS) {
                iterator.remove()
            }
        }
    }
}
