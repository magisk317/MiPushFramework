package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.runtime.core.DuplicateMessagePolicy

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class DuplicateMessagePolicyTest {
    private lateinit var policy: DuplicateMessagePolicy

    @BeforeEach
    fun setUp() {
        policy = DuplicateMessagePolicy()
    }


    @Test
    fun `message churn cannot grow dedupe state without bound`() {
        policy.clearAll()
        repeat(2_048 + 100) { index ->
            policy.checkAndMark("message-$index", nowMs = 1_000L)
        }

        assertEquals(2_048, policy.trackedMessageCount())
    }

    @Test
    fun `checkAndMark keeps duplicate within extended window`() {
        policy.clearAll()

        assertFalse(policy.checkAndMark("msg-1", nowMs = 1_000L))
        assertTrue(policy.checkAndMark("msg-1", nowMs = 31_000L))
        assertFalse(policy.checkAndMark("msg-1", nowMs = 92_000L))
    }

    @Test
    fun `same message id is independent across scopes`() {
        assertFalse(policy.checkAndMark("package.one", "shared-id", nowMs = 1_000L))
        assertFalse(policy.checkAndMark("package.two", "shared-id", nowMs = 1_001L))
        assertTrue(policy.checkAndMark("package.one", "shared-id", nowMs = 1_002L))
    }
}
