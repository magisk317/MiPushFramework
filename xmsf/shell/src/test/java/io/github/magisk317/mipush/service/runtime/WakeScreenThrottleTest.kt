package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WakeScreenThrottleTest {

    private class FakeClock(var now: Long) : () -> Long {
        override fun invoke(): Long = now
    }

    @Test
    fun `first request for a package is admitted`() {
        val throttle = WakeScreenThrottle(clock = { 0L }, minimumIntervalMillis = 5_000L)

        assertTrue(throttle.tryAcquireAt("com.example.a", 0L))
    }

    @Test
    fun `a second request inside the window is rejected`() {
        val throttle = WakeScreenThrottle(clock = { 0L }, minimumIntervalMillis = 5_000L)

        assertTrue(throttle.tryAcquireAt("com.example.a", 1_000L))
        assertFalse(throttle.tryAcquireAt("com.example.a", 5_999L))
    }

    @Test
    fun `a request at or after the window is admitted`() {
        val throttle = WakeScreenThrottle(clock = { 0L }, minimumIntervalMillis = 5_000L)

        assertTrue(throttle.tryAcquireAt("com.example.a", 1_000L))
        assertTrue(throttle.tryAcquireAt("com.example.a", 6_000L))
    }

    @Test
    fun `packages are throttled independently`() {
        val throttle = WakeScreenThrottle(clock = { 0L }, minimumIntervalMillis = 5_000L)

        assertTrue(throttle.tryAcquireAt("com.example.a", 0L))
        assertTrue(throttle.tryAcquireAt("com.example.b", 10L))
        assertFalse(throttle.tryAcquireAt("com.example.a", 20L))
        assertFalse(throttle.tryAcquireAt("com.example.b", 30L))
    }

    @Test
    fun `a zero interval admits every request`() {
        val throttle = WakeScreenThrottle(clock = { 0L }, minimumIntervalMillis = 0L)

        assertTrue(throttle.tryAcquireAt("com.example.a", 0L))
        assertTrue(throttle.tryAcquireAt("com.example.a", 0L))
    }

    @Test
    fun `a clock rollback starts a new epoch and is admitted`() {
        val throttle = WakeScreenThrottle(clock = { 0L }, minimumIntervalMillis = 5_000L)

        assertTrue(throttle.tryAcquireAt("com.example.a", 10_000L))
        // now < previous: treat as a new epoch rather than suppressing the wake forever
        assertTrue(throttle.tryAcquireAt("com.example.a", 9_000L))
        assertFalse(throttle.tryAcquireAt("com.example.a", 9_500L))
    }

    @Test
    fun `the tracked package set stays bounded and evicts the least recently admitted`() {
        val throttle = WakeScreenThrottle(
            clock = { 0L },
            minimumIntervalMillis = 5_000L,
            maxEntries = 2,
        )

        assertTrue(throttle.tryAcquireAt("com.example.a", 0L))
        assertTrue(throttle.tryAcquireAt("com.example.b", 1_000L))
        assertTrue(throttle.tryAcquireAt("com.example.c", 2_000L))
        assertEquals(2, throttle.trackedPackageCount())

        // "a" was evicted, so it is admitted again even though it is still inside its window.
        assertTrue(throttle.tryAcquireAt("com.example.a", 3_000L))
        assertEquals(2, throttle.trackedPackageCount())
    }

    @Test
    fun `the production clock is consulted when no timestamp is supplied`() {
        val clock = FakeClock(1_000L)
        val throttle = WakeScreenThrottle(clock = clock, minimumIntervalMillis = 5_000L)

        assertTrue(throttle.tryAcquire("com.example.a"))
        clock.now = 2_000L
        assertFalse(throttle.tryAcquire("com.example.a"))
        clock.now = 7_000L
        assertTrue(throttle.tryAcquire("com.example.a"))
    }

    @Test
    fun `invalid construction arguments are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            WakeScreenThrottle(clock = { 0L }, minimumIntervalMillis = -1L)
        }
        assertThrows(IllegalArgumentException::class.java) {
            WakeScreenThrottle(clock = { 0L }, maxEntries = 0)
        }
    }
}
