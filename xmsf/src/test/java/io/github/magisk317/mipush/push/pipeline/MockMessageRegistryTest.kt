package io.github.magisk317.mipush.push.pipeline

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MockMessageRegistryTest {

    @BeforeEach
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
