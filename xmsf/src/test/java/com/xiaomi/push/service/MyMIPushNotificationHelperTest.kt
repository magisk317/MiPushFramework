package com.xiaomi.push.service

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MyMIPushNotificationHelperTest {

    @Test
    fun `shouldPublishNotification only allows send message payloads`() {
        val sendMessage = XmPushActionContainer().apply { action = ActionType.SendMessage }
        val ackNotification = XmPushActionContainer().apply { action = ActionType.Notification }
        val command = XmPushActionContainer().apply { action = ActionType.Command }

        assertTrue(MyMIPushNotificationHelper.shouldPublishNotification(sendMessage))
        assertFalse(MyMIPushNotificationHelper.shouldPublishNotification(ackNotification))
        assertFalse(MyMIPushNotificationHelper.shouldPublishNotification(command))
        assertFalse(MyMIPushNotificationHelper.shouldPublishNotification(XmPushActionContainer()))
    }

    @Test
    fun `shouldDropReplayNotification drops messages older than notification session`() {
        val container = XmPushActionContainer().apply {
            action = ActionType.SendMessage
            metaInfo = PushMetaInfo().apply {
                id = "msg-1"
                setMessageTs(1_000L)
            }
        }

        assertTrue(
            MyMIPushNotificationHelper.shouldDropReplayNotification(
                container = container,
                sessionStartedAtMs = 1_001L,
            )
        )
        assertFalse(
            MyMIPushNotificationHelper.shouldDropReplayNotification(
                container = container,
                sessionStartedAtMs = 1_000L,
            )
        )
    }

    @Test
    fun `shouldDropReplayNotification keeps payloads without message timestamp`() {
        val container = XmPushActionContainer().apply {
            action = ActionType.SendMessage
            metaInfo = PushMetaInfo().apply { id = "msg-1" }
        }

        assertFalse(MyMIPushNotificationHelper.shouldDropReplayNotification(container, sessionStartedAtMs = 1_000_000L))
    }
}
