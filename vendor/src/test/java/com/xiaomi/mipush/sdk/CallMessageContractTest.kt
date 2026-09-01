package com.xiaomi.mipush.sdk

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class CallMessageContractTest {
    private class RecordingReceiver : PushMessageReceiver() {
        var received: CallMessage? = null

        override fun onCallMessage(callMessage: CallMessage) {
            received = callMessage
        }
    }

    @Test
    fun `notification style 6 pass-through is call message`() {
        val message = MiPushMessage().apply {
            passThrough = 1
            extra = mapOf("notification_style_type" to "6")
        }
        assertTrue(PushMessageReceiverDispatchPolicy.isCallMessage(message))
    }

    @Test
    fun `ordinary pass-through remains ordinary callback`() {
        val message = MiPushMessage().apply {
            passThrough = 1
            extra = mapOf("notification_style_type" to "0")
        }
        assertFalse(PushMessageReceiverDispatchPolicy.isCallMessage(message))
    }

    @Test
    fun `call message exposes only SDK public fields`() {
        val message = CallMessage("id", "payload")
        assertTrue(message.getMsgId() == "id")
        assertTrue(message.getMessage() == "payload")
    }

    @Test
    fun `call callback keeps the SDK receiver signature`() {
        val receiver = RecordingReceiver()
        receiver.onCallMessage(CallMessage("id", "payload"))

        assertEquals("id", receiver.received?.getMsgId())
        assertEquals("payload", receiver.received?.getMessage())
    }
}
