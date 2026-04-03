package com.magisk317.push.pipeline

import com.xiaomi.xmsf.runtime.PushRuntime
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MiPushRuntimeBridgeTest {

    @Test
    fun `shouldProcessPayloadIdentity blocks duplicate notification payload within extended window`() {
        PushRuntime.clearStateForTests()

        assertTrue(
            MiPushRuntimeBridge.shouldProcessPayloadIdentity(
                packageName = "com.tencent.mobileqq",
                actionName = "SendMessage",
                messageId = "msg-1",
                source = "ClientEventDispatcher.notifyPacketArrival(blob)",
                isAck = false,
                isMockReplay = false,
                payloadSize = 128,
            ),
        )
        assertFalse(
            MiPushRuntimeBridge.shouldProcessPayloadIdentity(
                packageName = "com.tencent.mobileqq",
                actionName = "SendMessage",
                messageId = "msg-1",
                source = "notification",
                isAck = false,
                isMockReplay = false,
                payloadSize = 128,
            ),
        )
    }
}
