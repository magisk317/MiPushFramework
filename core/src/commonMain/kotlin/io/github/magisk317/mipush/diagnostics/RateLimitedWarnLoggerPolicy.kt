package io.github.magisk317.mipush.diagnostics

import co.touchlab.kermit.Logger

/**
 * Platform-neutral rate-limited warning logger.
 *
 * Suppresses repeated warnings for the same key within a configurable time window.
 */
class RateLimitedWarnLoggerPolicy(
    private val nowProvider: () -> Long
) {
    private var gate = RateLimitGate(nowProvider)

    fun warn(
        logTag: String,
        key: String,
        message: String,
        throwable: Throwable? = null,
        windowMs: Long = 30_000L
    ) {
        if (!gate.shouldLog("$logTag:$key", windowMs)) return
        val msg = "[$key] $message"
        val logger = Logger.withTag(logTag)
        if (throwable == null) {
            logger.w { msg }
        } else {
            logger.e(throwable) { msg }
        }
    }

    fun reset() {
        gate = RateLimitGate(nowProvider)
    }

    class RateLimitGate(private val nowProvider: () -> Long) {
        private val lock = Any()
        private val lastLogAt = HashMap<String, Long>()

        fun shouldLog(key: String, windowMs: Long): Boolean {
            if (windowMs <= 0L) return true
            val now = nowProvider()
            return synchronized(lock) {
                val previous = lastLogAt[key]
                if (previous == null || now - previous >= windowMs) {
                    lastLogAt[key] = now
                    true
                } else {
                    false
                }
            }
        }
    }
}
