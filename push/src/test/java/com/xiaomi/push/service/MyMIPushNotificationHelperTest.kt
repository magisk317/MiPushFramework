package com.xiaomi.push.service

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

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
}
