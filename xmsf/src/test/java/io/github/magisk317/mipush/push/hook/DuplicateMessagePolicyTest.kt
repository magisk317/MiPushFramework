package io.github.magisk317.mipush.push.hook

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DuplicateMessagePolicyTest {

    @Test
    fun `checkAndMark keeps duplicate within extended window`() {
        DuplicateMessagePolicy.clearAllForTests()

        assertFalse(DuplicateMessagePolicy.checkAndMark("msg-1", nowMs = 1_000L))
        assertTrue(DuplicateMessagePolicy.checkAndMark("msg-1", nowMs = 31_000L))
        assertFalse(DuplicateMessagePolicy.checkAndMark("msg-1", nowMs = 92_000L))
    }
}
