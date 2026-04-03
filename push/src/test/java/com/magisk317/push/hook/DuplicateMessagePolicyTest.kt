package com.magisk317.push.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DuplicateMessagePolicyTest {

    @Test
    fun `checkAndMark keeps duplicate within extended window`() {
        DuplicateMessagePolicy.clearAllForTests()

        assertFalse(DuplicateMessagePolicy.checkAndMark("msg-1", nowMs = 1_000L))
        assertTrue(DuplicateMessagePolicy.checkAndMark("msg-1", nowMs = 31_000L))
        assertFalse(DuplicateMessagePolicy.checkAndMark("msg-1", nowMs = 92_000L))
    }
}
