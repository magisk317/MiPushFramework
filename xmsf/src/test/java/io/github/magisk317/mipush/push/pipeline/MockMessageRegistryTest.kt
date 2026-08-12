package io.github.magisk317.mipush.push.pipeline

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class MockMessageRegistryTest {

    @BeforeEach
    fun reset() {
        MockMessageRegistry.clearAllForTests()
    }

    @Test
    fun `random replay ids cannot grow registry without bound`() {
        repeat(MockMessageRegistry.MAX_MARKED_MESSAGES + 100) { index ->
            MockMessageRegistry.markMessageId("mock-$index")
        }

        assertEquals(MockMessageRegistry.MAX_MARKED_MESSAGES, MockMessageRegistry.markedMessageCount())
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

    @Test
    fun `container marks are scoped by package`() {
        val first = XmPushActionContainer().apply {
            packageName = "com.example.first"
            action = ActionType.SendMessage
            metaInfo = com.xiaomi.xmpush.thrift.PushMetaInfo().apply { id = "shared-id" }
        }
        val second = first.deepCopy().apply { packageName = "com.example.second" }

        MockMessageRegistry.mark(first)

        assertTrue(MockMessageRegistry.isMarked(first))
        assertFalse(MockMessageRegistry.isMarked(second))
    }
}
