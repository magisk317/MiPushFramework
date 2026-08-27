package io.github.magisk317.mipush.runtime.core

/**
 * Compatibility facade delegating to the platform-neutral [RegistrationThrottlePolicy].
 * Kept for source compatibility with existing callers.
 */
object RegistrationThrottle {
    const val THROTTLE_INTERVAL_MS = RegistrationThrottlePolicy.THROTTLE_INTERVAL_MS
    internal const val MAX_TRACKED_PACKAGES = 512

    private val policy = RegistrationThrottlePolicy(MAX_TRACKED_PACKAGES)

    @JvmStatic
    fun shouldThrottle(
        packageName: String,
        channelBound: Boolean,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean = policy.shouldThrottle(packageName, channelBound, nowMs)

    @JvmStatic
    fun reset() = policy.reset()

    @JvmStatic
    fun reset(packageName: String) = policy.reset(packageName)

    internal fun trackedPackageCount(): Int = policy.trackedPackageCount()
}
