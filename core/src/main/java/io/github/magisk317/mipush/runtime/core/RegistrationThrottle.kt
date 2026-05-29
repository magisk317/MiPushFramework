package io.github.magisk317.mipush.runtime.core

import java.util.concurrent.ConcurrentHashMap

/**
 * Throttles registration requests per package when the channel is not bound.
 *
 * When the channel is in unbind or binding state, apps may repeatedly call
 * tryForceRegister, creating a registration storm. This utility limits
 * same-package registration requests to at most once per [THROTTLE_INTERVAL_MS]
 * when the channel is not yet bound.
 */
object RegistrationThrottle {
    /** Minimum interval between registration attempts per package (30 seconds). */
    const val THROTTLE_INTERVAL_MS = 30_000L

    private val lastRegistrationTimeMs = ConcurrentHashMap<String, Long>()

    /**
     * Checks whether a registration request for [packageName] should be throttled.
     *
     * @param packageName the package requesting registration
     * @param channelBound true if the channel is currently in `binded` state
     * @param nowMs current time in milliseconds (injectable for testing)
     * @return true if the request should be throttled (dropped), false if it should proceed
     */
    @JvmStatic
    fun shouldThrottle(
        packageName: String,
        channelBound: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        // Never throttle when channel is bound — normal registration flow
        if (channelBound) return false

        val lastTime = lastRegistrationTimeMs[packageName]
        if (lastTime != null && (nowMs - lastTime) < THROTTLE_INTERVAL_MS) {
            return true
        }
        lastRegistrationTimeMs[packageName] = nowMs
        return false
    }

    /**
     * Resets throttle state. Useful when the channel successfully binds.
     */
    @JvmStatic
    fun reset() {
        lastRegistrationTimeMs.clear()
    }

    /**
     * Resets throttle state for a specific package.
     */
    @JvmStatic
    fun reset(packageName: String) {
        lastRegistrationTimeMs.remove(packageName)
    }
}
