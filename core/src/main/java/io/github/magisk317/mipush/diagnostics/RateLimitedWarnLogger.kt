package io.github.magisk317.mipush.diagnostics

import io.github.magisk317.mipush.runtime.store.kmp.RateLimitedWarnLoggerPolicy

/**
 * Android-side adapter delegating to the platform-neutral [RateLimitedWarnLoggerPolicy].
 * Kept for source compatibility with existing callers.
 */
object RateLimitedWarnLogger {
    private val policy = RateLimitedWarnLoggerPolicy { System.currentTimeMillis() }

    fun warn(
        logTag: String,
        key: String,
        message: String,
        throwable: Throwable? = null,
        windowMs: Long = 30_000L
    ) = policy.warn(logTag, key, message, throwable, windowMs)

    internal fun resetForTest(nowProvider: () -> Long = { System.currentTimeMillis() }) {
        policy.reset()
    }

    internal class RateLimitGate(
        private val nowProvider: () -> Long = { System.currentTimeMillis() }
    ) {
        private val delegate = RateLimitedWarnLoggerPolicy.RateLimitGate(nowProvider)

        fun shouldLog(key: String, windowMs: Long): Boolean =
            delegate.shouldLog(key, windowMs)
    }
}
