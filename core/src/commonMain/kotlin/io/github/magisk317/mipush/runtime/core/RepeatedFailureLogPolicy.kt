package io.github.magisk317.mipush.runtime.core

/**
 * Keeps one log line per failure key inside a window.
 *
 * List projections run the same configuration pass for every visible row, so one
 * per-row failure multiplies by the page size and drowns the rest of the route log.
 */
class RepeatedFailureLogPolicy(
    private val windowMs: Long = DEFAULT_WINDOW_MS,
    private val maxTrackedKeys: Int = DEFAULT_MAX_TRACKED_KEYS,
) {
    private val lock = Any()
    private val lastLoggedMs = LinkedHashMap<String, Long>(16, 0.75f, true)

    /** Returns true only for the first [key] seen in the current window. */
    fun shouldLog(key: String, nowMs: Long): Boolean {
        synchronized(lock) {
            pruneExpiredLocked(nowMs)
            if (lastLoggedMs.containsKey(key)) return false
            // lastLoggedMs is access-ordered, so its first key is the eldest entry.
            while (lastLoggedMs.size >= maxTrackedKeys) {
                val eldestKey = lastLoggedMs.keys.firstOrNull() ?: break
                lastLoggedMs.remove(eldestKey)
            }
            lastLoggedMs[key] = nowMs
            return true
        }
    }

    fun reset() {
        synchronized(lock) { lastLoggedMs.clear() }
    }

    internal fun trackedKeyCount(): Int = synchronized(lock) { lastLoggedMs.size }

    private fun pruneExpiredLocked(nowMs: Long) {
        val iterator = lastLoggedMs.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().value >= windowMs) {
                iterator.remove()
            }
        }
    }

    companion object {
        const val DEFAULT_WINDOW_MS = 60_000L
        const val DEFAULT_MAX_TRACKED_KEYS = 256
    }
}
