package io.github.magisk317.mipush.runtime.store.kmp

/**
 * Platform-neutral registration throttling policy.
 *
 * Limits same-package registration requests when the channel is not bound,
 * preventing registration storms from repeated tryForceRegister calls.
 */
class RegistrationThrottlePolicy(
    private val maxTrackedPackages: Int = 512
) {
    private val lock = Any()
    private val lastRegistrationTimeMs = LinkedHashMap<String, Long>(16, 0.75f, true)

    fun shouldThrottle(
        packageName: String,
        channelBound: Boolean,
        nowMs: Long
    ): Boolean {
        if (channelBound) return false
        synchronized(lock) {
            pruneExpiredLocked(nowMs)
            val lastTime = lastRegistrationTimeMs[packageName]
            if (lastTime != null && (nowMs - lastTime) < THROTTLE_INTERVAL_MS) {
                return true
            }
            while (lastRegistrationTimeMs.size >= maxTrackedPackages) {
                val iterator = lastRegistrationTimeMs.entries.iterator()
                if (!iterator.hasNext()) break
                iterator.next()
                iterator.remove()
            }
            lastRegistrationTimeMs[packageName] = nowMs
            return false
        }
    }

    fun reset() {
        synchronized(lock) { lastRegistrationTimeMs.clear() }
    }

    fun reset(packageName: String) {
        synchronized(lock) { lastRegistrationTimeMs.remove(packageName) }
    }

    fun trackedPackageCount(): Int = synchronized(lock) { lastRegistrationTimeMs.size }

    private fun pruneExpiredLocked(nowMs: Long) {
        val iterator = lastRegistrationTimeMs.entries.iterator()
        while (iterator.hasNext()) {
            if (nowMs - iterator.next().value >= THROTTLE_INTERVAL_MS) {
                iterator.remove()
            }
        }
    }

    companion object {
        const val THROTTLE_INTERVAL_MS = 30_000L
    }
}
