package com.magisk317.diagnostics

import io.github.aakira.napier.Napier

object RateLimitedWarnLogger {
    @Volatile
    private var gate = RateLimitGate()

    fun warn(
        logTag: String,
        key: String,
        message: String,
        throwable: Throwable? = null,
        windowMs: Long = 30_000L
    ) {
        if (!gate.shouldLog("$logTag:$key", windowMs)) return
        val msg = "[$key] $message"
        if (throwable == null) {
            Napier.w(msg, tag = logTag)
        } else {
            Napier.e(msg, throwable, tag = logTag)
        }
    }

    internal fun resetForTest(nowProvider: () -> Long = { System.currentTimeMillis() }) {
        gate = RateLimitGate(nowProvider)
    }

    internal class RateLimitGate(
        private val nowProvider: () -> Long = { System.currentTimeMillis() }
    ) {
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
