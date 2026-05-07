package com.xiaomi.push.service

import android.app.PendingIntent
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MIPushNotificationActionSupportTest {

    @Test
    fun `default pending intent flags include update current and immutable`() {
        assertEquals(
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            MIPushNotificationActionSupport.DEFAULT_PENDING_INTENT_FLAGS,
        )
    }
}
