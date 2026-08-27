package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationThrottlePolicyTest {

    @Test
    fun `never throttles when channel is bound`() {
        val policy = RegistrationThrottlePolicy()
        assertFalse(policy.shouldThrottle("com.example", channelBound = true, nowMs = 1000L))
    }

    @Test
    fun `first unbound request passes`() {
        val policy = RegistrationThrottlePolicy()
        assertFalse(policy.shouldThrottle("com.example", channelBound = false, nowMs = 1000L))
    }

    @Test
    fun `second request within window is throttled`() {
        val policy = RegistrationThrottlePolicy()
        policy.shouldThrottle("com.example", false, nowMs = 1000L)
        assertTrue(policy.shouldThrottle("com.example", false, nowMs = 1000L + 29_999L))
    }

    @Test
    fun `request after window passes`() {
        val policy = RegistrationThrottlePolicy()
        policy.shouldThrottle("com.example", false, nowMs = 1000L)
        assertFalse(policy.shouldThrottle("com.example", false, nowMs = 1000L + 30_001L))
    }

    @Test
    fun `different packages are independent`() {
        val policy = RegistrationThrottlePolicy()
        policy.shouldThrottle("com.a", false, nowMs = 1000L)
        assertFalse(policy.shouldThrottle("com.b", false, nowMs = 1500L))
    }

    @Test
    fun `reset clears all tracked packages`() {
        val policy = RegistrationThrottlePolicy()
        policy.shouldThrottle("com.a", false, nowMs = 1000L)
        policy.reset()
        assertEquals(0, policy.trackedPackageCount())
    }
}
