package com.xiaomi.xmsf.push.service.notificationcollection

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationListenerPolicyTest {
    @Test
    fun `accepts current user and user all notifications`() {
        assertTrue(NotificationListener.acceptsUser(eventUserId = 0, currentUserId = 0))
        assertTrue(NotificationListener.acceptsUser(eventUserId = -1, currentUserId = 0))
        assertTrue(NotificationListener.acceptsUser(eventUserId = 999, currentUserId = -1))
    }

    @Test
    fun `rejects notifications from another concrete user`() {
        assertFalse(NotificationListener.acceptsUser(eventUserId = 999, currentUserId = 0))
        assertFalse(NotificationListener.acceptsUser(eventUserId = 0, currentUserId = 999))
    }
}
