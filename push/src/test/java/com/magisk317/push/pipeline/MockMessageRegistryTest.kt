package com.magisk317.push.pipeline

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MockMessageRegistryTest {

    @Before
    fun reset() {
        MockMessageRegistry.clearAllForTests()
    }

    @Test
    fun markMessageId_marksAndClearRemoves() {
        val id = "mock-id-1"
        MockMessageRegistry.markMessageId(id)
        assertTrue(MockMessageRegistry.isMarked(id))

        MockMessageRegistry.clear(id)
        assertFalse(MockMessageRegistry.isMarked(id))
    }

    @Test
    fun blankMessageIdIsIgnored() {
        MockMessageRegistry.markMessageId("")
        MockMessageRegistry.markMessageId(" ")
        assertFalse(MockMessageRegistry.isMarked(""))
        assertFalse(MockMessageRegistry.isMarked(" "))
    }
}
