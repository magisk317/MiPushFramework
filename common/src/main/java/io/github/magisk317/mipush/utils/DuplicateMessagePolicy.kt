package io.github.magisk317.mipush.utils

import java.util.LinkedHashMap

/**
 * Message duplicate policy based on scope + messageId + time window.
 *
 * checkAndMark(scope, messageId):
 * - false -> first seen in window
 * - true  -> duplicated within window
 */
object DuplicateMessagePolicy {
    private const val DEDUP_WINDOW_MS = 60_000L
    internal const val MAX_TRACKED_MESSAGES = 2_048
    private val lock = Any()
    private const val LEGACY_SCOPE = "__legacy__"
    private val seen = LinkedHashMap<ScopedMessageId, Long>()

    private data class ScopedMessageId(
        val scope: String,
        val messageId: String,
    )

    @JvmStatic
    fun checkAndMark(messageId: String?, nowMs: Long = System.currentTimeMillis()): Boolean {
        return checkAndMark(LEGACY_SCOPE, messageId, nowMs)
    }

    @JvmStatic
    fun checkAndMark(
        scope: String?,
        messageId: String?,
        nowMs: Long = System.currentTimeMillis(),
    ): Boolean {
        if (messageId.isNullOrBlank()) return false
        val scopedId = ScopedMessageId(scope.orEmpty(), messageId)
        synchronized(lock) {
            pruneExpiredLocked(nowMs)
            val previous = seen[scopedId]
            val duplicated = previous != null && (nowMs - previous) <= DEDUP_WINDOW_MS
            if (previous == null && seen.size >= MAX_TRACKED_MESSAGES) {
                seen.entries.iterator().run {
                    if (hasNext()) {
                        next()
                        remove()
                    }
                }
            }
            seen[scopedId] = nowMs
            return duplicated
        }
    }

    @JvmStatic
    fun clearAllForTests() {
        synchronized(lock) {
            seen.clear()
        }
    }

    internal fun trackedMessageCount(): Int = synchronized(lock) { seen.size }

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
