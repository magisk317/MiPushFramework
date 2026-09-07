package io.github.magisk317.mipush.runtime.core

/**
 * Bounds registration-task processing caused by repeated network-available broadcasts.
 *
 * The timestamp is monotonic elapsed time supplied by the Android adapter. The check and
 * timestamp update are one critical section, so concurrent broadcasts can claim one window only.
 */
class NetworkRegistrationThrottlePolicy(
    private val maxTrackedUsers: Int = 16,
) {
    private val lock = Any()
    private val lastProcessElapsedMs = LinkedHashMap<Int, Long>(4, 0.75f, true)

    fun shouldThrottle(androidUserId: Int, nowElapsedMs: Long): Boolean {
        require(androidUserId >= 0) { "Invalid Android user id: $androidUserId" }
        synchronized(lock) {
            val previous = lastProcessElapsedMs[androidUserId]
            if (previous != null) {
                val elapsed = nowElapsedMs - previous
                if (elapsed in 0 until MIN_PROCESS_INTERVAL_MS) {
                    return true
                }
            }
            while (lastProcessElapsedMs.size >= maxTrackedUsers) {
                val oldest = lastProcessElapsedMs.entries.iterator()
                if (!oldest.hasNext()) break
                oldest.next()
                oldest.remove()
            }
            lastProcessElapsedMs[androidUserId] = nowElapsedMs
            return false
        }
    }

    fun reset() {
        synchronized(lock) { lastProcessElapsedMs.clear() }
    }

    internal fun trackedUserCount(): Int = synchronized(lock) { lastProcessElapsedMs.size }

    companion object {
        const val MIN_PROCESS_INTERVAL_MS = 5 * 60 * 1000L
    }
}
