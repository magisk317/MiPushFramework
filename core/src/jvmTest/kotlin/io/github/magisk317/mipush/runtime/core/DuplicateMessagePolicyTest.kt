package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DuplicateMessagePolicyKmpTest {

    @Test
    fun `first message is not duplicate`() {
        val policy = DuplicateMessagePolicy()
        assertFalse(policy.checkAndMark("scope", "msg1", nowMs = 1000L))
    }

    @Test
    fun `same message within window is duplicate`() {
        val policy = DuplicateMessagePolicy()
        policy.checkAndMark("scope", "msg1", nowMs = 1000L)
        assertTrue(policy.checkAndMark("scope", "msg1", nowMs = 1000L + 59_999L))
    }

    @Test
    fun `same message after window is not duplicate`() {
        val policy = DuplicateMessagePolicy()
        policy.checkAndMark("scope", "msg1", nowMs = 1000L)
        assertFalse(policy.checkAndMark("scope", "msg1", nowMs = 1000L + 60_001L))
    }

    @Test
    fun `different scopes are independent`() {
        val policy = DuplicateMessagePolicy()
        policy.checkAndMark("scope1", "msg1", nowMs = 1000L)
        assertFalse(policy.checkAndMark("scope2", "msg1", nowMs = 2000L))
    }

    @Test
    fun `blank messageId is never duplicate`() {
        val policy = DuplicateMessagePolicy()
        assertFalse(policy.checkAndMark("scope", "", nowMs = 1000L))
        assertFalse(policy.checkAndMark("scope", null, nowMs = 1000L))
    }

    @Test
    fun `clearAll resets tracked count`() {
        val policy = DuplicateMessagePolicy()
        policy.checkAndMark("s", "m", nowMs = 1000L)
        assertEquals(1, policy.trackedMessageCount())
        policy.clearAll()
        assertEquals(0, policy.trackedMessageCount())
    }
}
