package com.xiaomi.push.service

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class MIPushEventProcessorTest {
    @Test
    fun `buildContainer returns decoded thrift container`() {
        val payload = samplePayload()

        val container = MIPushEventProcessor.buildContainer(payload)

        assertNotNull(container)
        assertEquals("com.example.app", container?.packageName)
        assertEquals(ActionType.Notification, container?.action)
    }

    private fun samplePayload(): ByteArray {
        val notification = XmPushActionNotification().apply {
            setAppId("app-id")
            setType("type")
            setId("message-id")
            setRequireAck(false)
        }
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.generateRequestContainer("com.example.app", "app-id", notification, ActionType.Notification),
        )
    }
}
