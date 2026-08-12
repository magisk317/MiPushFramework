package io.github.magisk317.mipush.utils

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DuplicateMessagePolicyTest {

    @Test
    fun `message churn cannot grow dedupe state without bound`() {
        DuplicateMessagePolicy.clearAllForTests()
        repeat(DuplicateMessagePolicy.MAX_TRACKED_MESSAGES + 100) { index ->
            DuplicateMessagePolicy.checkAndMark("message-$index", nowMs = 1_000L)
        }

        assertEquals(DuplicateMessagePolicy.MAX_TRACKED_MESSAGES, DuplicateMessagePolicy.trackedMessageCount())
    }

    @Test
    fun `checkAndMark keeps duplicate within extended window`() {
        DuplicateMessagePolicy.clearAllForTests()

        assertFalse(DuplicateMessagePolicy.checkAndMark("msg-1", nowMs = 1_000L))
        assertTrue(DuplicateMessagePolicy.checkAndMark("msg-1", nowMs = 31_000L))
        assertFalse(DuplicateMessagePolicy.checkAndMark("msg-1", nowMs = 92_000L))
    }

    @Test
    fun `same message id is independent across scopes`() {
        assertFalse(DuplicateMessagePolicy.checkAndMark("package.one", "shared-id", nowMs = 1_000L))
        assertFalse(DuplicateMessagePolicy.checkAndMark("package.two", "shared-id", nowMs = 1_001L))
        assertTrue(DuplicateMessagePolicy.checkAndMark("package.one", "shared-id", nowMs = 1_002L))
    }
}
