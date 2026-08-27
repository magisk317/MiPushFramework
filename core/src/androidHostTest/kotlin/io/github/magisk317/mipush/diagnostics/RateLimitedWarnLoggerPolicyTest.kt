package io.github.magisk317.mipush.diagnostics

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RateLimitedWarnLoggerPolicyTest {

    @Test
    fun `rate limit gate blocks within window`() {
        var now = 1_000L
        val gate = RateLimitedWarnLoggerPolicy.RateLimitGate { now }
        assertTrue(gate.shouldLog("k1", 30_000L))
        now += 1_000L
        assertFalse(gate.shouldLog("k1", 30_000L))
        now += 29_000L
        assertTrue(gate.shouldLog("k1", 30_000L))
    }

    @Test
    fun `rate limit gate allows different keys`() {
        var now = 5_000L
        val gate = RateLimitedWarnLoggerPolicy.RateLimitGate { now }
        assertTrue(gate.shouldLog("k1", 30_000L))
        assertTrue(gate.shouldLog("k2", 30_000L))
    }
}
