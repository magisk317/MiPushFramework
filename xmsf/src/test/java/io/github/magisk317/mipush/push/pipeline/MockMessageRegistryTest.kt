package io.github.magisk317.mipush.push.pipeline

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
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

    @Test
    fun markContainerFallsBackToPayloadFingerprintWhenMessageIdIsMissing() {
        val container = XmPushActionContainer().apply {
            packageName = "com.example"
            action = ActionType.SendMessage
            isRequest = false
        }

        MockMessageRegistry.mark(container)

        assertTrue(MockMessageRegistry.isMarked(container.deepCopy()))
    }
}
