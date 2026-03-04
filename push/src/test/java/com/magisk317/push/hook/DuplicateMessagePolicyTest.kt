package com.magisk317.push.hook

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DuplicateMessagePolicyTest {

    @Before
    fun reset() {
        DuplicateMessagePolicy.clearAllForTests()
    }

    @Test
    fun checkAndMark_detectsDuplicateWithinWindow() {
        val now = 1_000L
        assertFalse(DuplicateMessagePolicy.checkAndMark("id-1", now))
        assertTrue(DuplicateMessagePolicy.checkAndMark("id-1", now + 9_000L))
    }

    @Test
    fun checkAndMark_expiresAfterWindow() {
        val now = 2_000L
        assertFalse(DuplicateMessagePolicy.checkAndMark("id-2", now))
        assertFalse(DuplicateMessagePolicy.checkAndMark("id-2", now + 11_000L))
    }

    @Test
    fun checkAndMark_blankMessageIdIsNeverDuplicate() {
        assertFalse(DuplicateMessagePolicy.checkAndMark(""))
        assertFalse(DuplicateMessagePolicy.checkAndMark(" "))
        assertFalse(DuplicateMessagePolicy.checkAndMark(null))
    }
}
