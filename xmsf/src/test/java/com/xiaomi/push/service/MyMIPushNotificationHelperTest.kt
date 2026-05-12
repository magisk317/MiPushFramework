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
    fun `shouldDispatchNonDisplayPayload allows only command-style server results`() {
        val registrationResult = XmPushActionContainer().apply {
            action = ActionType.Registration
            isRequest = false
        }
        val registrationRequest = XmPushActionContainer().apply {
            action = ActionType.Registration
            isRequest = true
        }
        val commandResult = XmPushActionContainer().apply {
            action = ActionType.Command
            isRequest = false
        }
        val notification = XmPushActionContainer().apply {
            action = ActionType.Notification
            isRequest = false
        }
        val sendMessage = XmPushActionContainer().apply {
            action = ActionType.SendMessage
            isRequest = false
        }

        assertTrue(MyMIPushNotificationHelper.shouldDispatchNonDisplayPayload(registrationResult))
        assertTrue(MyMIPushNotificationHelper.shouldDispatchNonDisplayPayload(commandResult))
        assertFalse(MyMIPushNotificationHelper.shouldDispatchNonDisplayPayload(registrationRequest))
        assertFalse(MyMIPushNotificationHelper.shouldDispatchNonDisplayPayload(notification))
        assertFalse(MyMIPushNotificationHelper.shouldDispatchNonDisplayPayload(sendMessage))
    }

    @Test
    fun `shouldDropReplayNotification drops messages older than replay window`() {
        val sixHoursMs = 6 * 60 * 60 * 1000L
        val container = XmPushActionContainer().apply {
            action = ActionType.SendMessage
            metaInfo = PushMetaInfo().apply {
                id = "msg-1"
                setMessageTs(1_000L)
            }
        }

        // messageTs is 7 hours before session -> should drop
        assertTrue(
            MyMIPushNotificationHelper.shouldDropReplayNotification(
                container = container,
                sessionStartedAtMs = 1_000L + sixHoursMs + 3_600_000L,
            )
        )
        // messageTs is 5 hours before session -> within window, should keep
        assertFalse(
            MyMIPushNotificationHelper.shouldDropReplayNotification(
                container = container,
                sessionStartedAtMs = 1_000L + sixHoursMs - 3_600_000L,
            )
        )
        // messageTs equals session -> should keep
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
