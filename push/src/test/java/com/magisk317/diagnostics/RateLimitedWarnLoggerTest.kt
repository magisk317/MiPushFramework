package com.magisk317.diagnostics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RateLimitedWarnLoggerTest {

    @Test
    fun rateLimitGate_blocksLogsWithinWindow() {
        var now = 1_000L
        val gate = RateLimitedWarnLogger.RateLimitGate { now }

        assertTrue(gate.shouldLog("k1", 30_000L))
        now += 1_000L
        assertFalse(gate.shouldLog("k1", 30_000L))
        now += 29_000L
        assertTrue(gate.shouldLog("k1", 30_000L))
    }

    @Test
    fun rateLimitGate_allowsDifferentKeys() {
        var now = 5_000L
        val gate = RateLimitedWarnLogger.RateLimitGate { now }

        assertTrue(gate.shouldLog("k1", 30_000L))
        assertTrue(gate.shouldLog("k2", 30_000L))
    }
}
