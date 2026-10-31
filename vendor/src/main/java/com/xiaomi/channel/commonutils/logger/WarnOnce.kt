package com.xiaomi.channel.commonutils.logger

import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps one warning per failure signature for probes that fail the same way on every call.
 *
 * Reflection probes against absent platform APIs (a telephony method the ROM does not expose, a
 * NotificationManager call the system rejects) repeat on every notification, and WARN is kept in
 * the shipped runtime log — so a single absent API used to contribute ~78 lines per session while
 * saying nothing new after the first one. The first occurrence warns, later ones with the same
 * signature stay silent for the life of the process.
 */
object WarnOnce {
    /**
     * Bound on tracked signatures. The key space is normally method x class, but a caller that
     * builds an unbounded key would otherwise grow the set forever; past the cap the registry
     * restarts, which may repeat a warning rather than suppress it.
     */
    const val MAX_TRACKED_KEYS = 256

    private val warned = ConcurrentHashMap.newKeySet<String>()

    /** Returns true only the first time [key] is seen. */
    fun shouldWarn(key: String): Boolean {
        if (warned.contains(key)) return false
        if (warned.size >= MAX_TRACKED_KEYS) warned.clear()
        warned.add(key)
        return true
    }

    fun reset() {
        warned.clear()
    }

    internal fun trackedCount(): Int = warned.size
}
