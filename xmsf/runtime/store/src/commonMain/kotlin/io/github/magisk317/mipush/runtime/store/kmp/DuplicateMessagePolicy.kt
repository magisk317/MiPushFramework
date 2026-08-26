package io.github.magisk317.mipush.runtime.store.kmp

/**
 * Platform-neutral message duplicate detection based on scope + messageId + time window.
 */
class DuplicateMessagePolicy(
    private val maxTrackedMessages: Int = 2_048,
    private val dedupWindowMs: Long = 60_000L
) {
    private val lock = Any()
    private val seen = LinkedHashMap<ScopedMessageId, Long>()

    fun checkAndMark(messageId: String?, nowMs: Long): Boolean {
        return checkAndMark(null, messageId, nowMs)
    }

    fun checkAndMark(scope: String?, messageId: String?, nowMs: Long): Boolean {
        if (messageId.isNullOrBlank()) return false
        val scopedId = ScopedMessageId(scope.orEmpty(), messageId)
        synchronized(lock) {
            pruneExpiredLocked(nowMs)
            val previous = seen[scopedId]
            val duplicated = previous != null && (nowMs - previous) <= dedupWindowMs
            if (previous == null && seen.size >= maxTrackedMessages) {
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

    fun clearAll() {
        synchronized(lock) { seen.clear() }
    }

    fun trackedMessageCount(): Int = synchronized(lock) { seen.size }

    private fun pruneExpiredLocked(nowMs: Long) {
        val iterator = seen.entries.iterator()
        while (iterator.hasNext()) {
            if ((nowMs - iterator.next().value) > dedupWindowMs) {
                iterator.remove()
            }
        }
    }

    private data class ScopedMessageId(val scope: String, val messageId: String)

    companion object {
        const val LEGACY_SCOPE = "__legacy__"
    }
}
