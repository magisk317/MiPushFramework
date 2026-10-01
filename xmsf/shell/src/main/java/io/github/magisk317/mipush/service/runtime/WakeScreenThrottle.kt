package io.github.magisk317.mipush.service.runtime

import java.util.LinkedHashMap

/**
 * Process-local gate for explicit screen-wake requests, keyed by source package.
 *
 * A notification configured with the `wake` operation used to acquire a wake lock on every
 * delivery, so a chatty sender could keep holding the device awake through repeated pushes.
 * The gate admits the first request for a package and then at most one per
 * [minimumIntervalMillis]; the tracking map is bounded by [maxEntries] with access-order
 * eviction so a long tail of packages cannot grow it without limit.
 *
 * Deliberately free of Android APIs so timing and eviction behaviour are testable on the JVM.
 * A clock rollback (or an elapsed-counter overflow) is treated as a new epoch and admitted,
 * so a clock anomaly cannot suppress wakes indefinitely.
 *
 * Callers must consult this immediately before acquiring a wake lock; it does not gate
 * notification publication or any other dispatch phase.
 */
internal class WakeScreenThrottle(
    private val clock: () -> Long,
    private val minimumIntervalMillis: Long = DEFAULT_MIN_INTERVAL_MILLIS,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
) {
    private val lock = Any()

    /** Access-order map: the eldest entry is the least recently admitted package. */
    private val lastAdmittedByPackage = LinkedHashMap<String, Long>(16, 0.75f, true)

    init {
        require(minimumIntervalMillis >= 0L) { "minimumIntervalMillis must be non-negative" }
        require(maxEntries > 0) { "maxEntries must be positive" }
    }

    /** Returns true when the caller may acquire a wake lock for [packageName]. */
    fun tryAcquire(packageName: String): Boolean = tryAcquireAt(packageName, clock())

    /** Deterministic entry point used by JVM tests. */
    internal fun tryAcquireAt(packageName: String, nowElapsedRealtime: Long): Boolean {
        synchronized(lock) {
            val previous = lastAdmittedByPackage[packageName]
            if (previous != null && nowElapsedRealtime >= previous) {
                val elapsed = nowElapsedRealtime - previous
                if (elapsed >= 0L && elapsed < minimumIntervalMillis) {
                    return false
                }
            }
            // A backward jump (now < previous) or an elapsed-counter overflow reaches this
            // branch on purpose and starts a new clock epoch.
            lastAdmittedByPackage[packageName] = nowElapsedRealtime
            trimToBound()
            return true
        }
    }

    /** Visible for tests verifying the bounded-cache contract. */
    internal fun trackedPackageCount(): Int = synchronized(lock) { lastAdmittedByPackage.size }

    private fun trimToBound() {
        if (lastAdmittedByPackage.size <= maxEntries) return
        val iterator = lastAdmittedByPackage.entries.iterator()
        while (lastAdmittedByPackage.size > maxEntries && iterator.hasNext()) {
            iterator.next()
            iterator.remove()
        }
    }

    internal companion object {
        /** Stock `wake` deliveries arrive in bursts; 5s per package keeps a burst to one lock. */
        const val DEFAULT_MIN_INTERVAL_MILLIS = 5_000L

        const val DEFAULT_MAX_ENTRIES = 128
    }
}
