package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NetworkRegistrationThrottlePolicyTest {
    @Test
    fun `first request passes and burst is throttled`() {
        val policy = NetworkRegistrationThrottlePolicy()

        assertFalse(policy.shouldThrottle(androidUserId = 0, nowElapsedMs = 1_000L))
        assertTrue(policy.shouldThrottle(androidUserId = 0, nowElapsedMs = 1_001L))
    }

    @Test
    fun `request at interval boundary passes`() {
        val policy = NetworkRegistrationThrottlePolicy()

        policy.shouldThrottle(androidUserId = 0, nowElapsedMs = 1_000L)

        assertFalse(
            policy.shouldThrottle(
                androidUserId = 0,
                nowElapsedMs = 1_000L + NetworkRegistrationThrottlePolicy.MIN_PROCESS_INTERVAL_MS,
            ),
        )
    }

    @Test
    fun `users have independent registration windows`() {
        val policy = NetworkRegistrationThrottlePolicy()

        assertFalse(policy.shouldThrottle(androidUserId = 0, nowElapsedMs = 1_000L))
        assertFalse(policy.shouldThrottle(androidUserId = 999, nowElapsedMs = 1_001L))
        assertTrue(policy.shouldThrottle(androidUserId = 0, nowElapsedMs = 1_002L))
    }

    @Test
    fun `clock rollback does not suppress the next request`() {
        val policy = NetworkRegistrationThrottlePolicy()

        policy.shouldThrottle(androidUserId = 0, nowElapsedMs = 10_000L)

        assertFalse(policy.shouldThrottle(androidUserId = 0, nowElapsedMs = 9_000L))
    }

    @Test
    fun `tracked users remain bounded`() {
        val policy = NetworkRegistrationThrottlePolicy(maxTrackedUsers = 2)

        policy.shouldThrottle(androidUserId = 0, nowElapsedMs = 1L)
        policy.shouldThrottle(androidUserId = 1, nowElapsedMs = 2L)
        policy.shouldThrottle(androidUserId = 2, nowElapsedMs = 3L)

        assertEquals(2, policy.trackedUserCount())
    }
}
