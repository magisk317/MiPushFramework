package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random

/**
 * Property-based tests for RegistrationThrottle.
 *
 * **Validates: Requirements 2.4, 9.2, 10.2**
 *
 * Uses JUnit 5 with manual random input generation (consistent with batch 1 pattern).
 */
class RegistrationThrottleTest {

    @BeforeEach
    fun setUp() {
        RegistrationThrottle.reset()
    }

    /**
     * Property 4: RegistrationThrottle channelBound bypass —
     * verify `shouldThrottle(packageName, channelBound=true, nowMs)` always returns `false`
     * for random package names and timestamps.
     *
     * **Validates: Requirements 9.2**
     */
    @RepeatedTest(100)
    fun `shouldThrottle returns false when channelBound is true for any input`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val packageName = generateRandomPackageName(rng)
        val nowMs = rng.nextLong(0, Long.MAX_VALUE / 2)

        // Even after a previous non-bound call that records state,
        // channelBound=true should still bypass throttling
        RegistrationThrottle.shouldThrottle(packageName, channelBound = false, nowMs = nowMs)

        val result = RegistrationThrottle.shouldThrottle(
            packageName = packageName,
            channelBound = true,
            nowMs = nowMs + rng.nextLong(0, RegistrationThrottle.THROTTLE_INTERVAL_MS)
        )

        assertFalse(
            result,
            "channelBound=true should never throttle, but got true for " +
                "packageName='$packageName', seed=$seed"
        )
    }

    /**
     * Property 4 (extended): channelBound bypass holds regardless of prior state.
     * Multiple rapid calls with channelBound=true never throttle.
     *
     * **Validates: Requirements 9.2**
     */
    @RepeatedTest(50)
    fun `multiple rapid channelBound true calls never throttle`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val packageName = generateRandomPackageName(rng)
        val baseTime = rng.nextLong(1_000_000L, 2_000_000_000L)

        // Make many rapid calls with channelBound=true — none should throttle
        repeat(rng.nextInt(5, 20)) { i ->
            val result = RegistrationThrottle.shouldThrottle(
                packageName = packageName,
                channelBound = true,
                nowMs = baseTime + i // 1ms apart — well within throttle interval
            )
            assertFalse(
                result,
                "channelBound=true call #$i should not throttle, seed=$seed"
            )
        }
    }

    /**
     * Property 5: RegistrationThrottle interval throttling —
     * verify first call returns `false`, subsequent call within `THROTTLE_INTERVAL_MS`
     * returns `true`.
     *
     * **Validates: Requirements 9.2**
     */
    @RepeatedTest(100)
    fun `first call returns false and subsequent call within interval returns true`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val packageName = generateRandomPackageName(rng)
        val t1 = rng.nextLong(1_000_000L, 2_000_000_000L)

        // First call should not throttle
        val firstResult = RegistrationThrottle.shouldThrottle(
            packageName = packageName,
            channelBound = false,
            nowMs = t1
        )
        assertFalse(
            firstResult,
            "First call should not be throttled for packageName='$packageName', seed=$seed"
        )

        // Second call within THROTTLE_INTERVAL_MS should throttle
        val delta = rng.nextLong(1, RegistrationThrottle.THROTTLE_INTERVAL_MS)
        val t2 = t1 + delta

        val secondResult = RegistrationThrottle.shouldThrottle(
            packageName = packageName,
            channelBound = false,
            nowMs = t2
        )
        assertTrue(
            secondResult,
            "Second call within interval (delta=${delta}ms) should be throttled " +
                "for packageName='$packageName', seed=$seed"
        )
    }

    /**
     * Property 5 (extended): After the throttle interval expires, the next call
     * should not be throttled.
     *
     * **Validates: Requirements 9.2**
     */
    @RepeatedTest(50)
    fun `call after throttle interval expires is not throttled`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val packageName = generateRandomPackageName(rng)
        val t1 = rng.nextLong(1_000_000L, 2_000_000_000L)

        // First call — not throttled
        val firstResult = RegistrationThrottle.shouldThrottle(
            packageName = packageName,
            channelBound = false,
            nowMs = t1
        )
        assertFalse(firstResult, "First call should not be throttled, seed=$seed")

        // Call after interval expires — should not be throttled
        val t2 = t1 + RegistrationThrottle.THROTTLE_INTERVAL_MS + rng.nextLong(0, 100_000L)

        val afterIntervalResult = RegistrationThrottle.shouldThrottle(
            packageName = packageName,
            channelBound = false,
            nowMs = t2
        )
        assertFalse(
            afterIntervalResult,
            "Call after interval expiry should not be throttled, seed=$seed"
        )
    }

    /**
     * Helper: generates a random package name like "com.example.app123".
     */
    private fun generateRandomPackageName(rng: Random): String {
        val segments = rng.nextInt(2, 5)
        return (1..segments).joinToString(".") {
            val len = rng.nextInt(3, 10)
            (1..len).map { ('a' + rng.nextInt(26)) }.joinToString("")
        }
    }
}
