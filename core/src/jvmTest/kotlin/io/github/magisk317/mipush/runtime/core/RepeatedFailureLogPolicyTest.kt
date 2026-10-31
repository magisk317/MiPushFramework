package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RepeatedFailureLogPolicyTest {
    @Test
    fun `first occurrence of a key is logged`() {
        val policy = RepeatedFailureLogPolicy(windowMs = 1_000L)
        assertTrue(policy.shouldLog("com.example|Notification", nowMs = 0L))
    }

    @Test
    fun `repeat inside the window is suppressed`() {
        val policy = RepeatedFailureLogPolicy(windowMs = 1_000L)
        assertTrue(policy.shouldLog("com.example|Notification", nowMs = 0L))
        assertFalse(policy.shouldLog("com.example|Notification", nowMs = 999L))
    }

    @Test
    fun `same key is allowed again once the window elapsed`() {
        val policy = RepeatedFailureLogPolicy(windowMs = 1_000L)
        assertTrue(policy.shouldLog("com.example|Notification", nowMs = 0L))
        assertTrue(policy.shouldLog("com.example|Notification", nowMs = 1_000L))
    }

    @Test
    fun `distinct keys are tracked independently`() {
        val policy = RepeatedFailureLogPolicy(windowMs = 1_000L)
        assertTrue(policy.shouldLog("a|Notification", nowMs = 0L))
        assertTrue(policy.shouldLog("b|Notification", nowMs = 0L))
        assertEquals(2, policy.trackedKeyCount())
    }

    @Test
    fun `expired keys are pruned instead of evicting live ones`() {
        val policy = RepeatedFailureLogPolicy(windowMs = 1_000L, maxTrackedKeys = 2)
        assertTrue(policy.shouldLog("a|Notification", nowMs = 0L))
        assertTrue(policy.shouldLog("b|Notification", nowMs = 500L))
        // Still inside the window, so the cap evicts the eldest entry rather than a fresh one.
        assertTrue(policy.shouldLog("c|Notification", nowMs = 900L))
        assertEquals(2, policy.trackedKeyCount())
        // Expired entries are pruned on the next call, so the cap never hides a live failure.
        assertTrue(policy.shouldLog("d|Notification", nowMs = 5_000L))
        assertEquals(1, policy.trackedKeyCount())
    }
}
