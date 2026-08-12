package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NativeNotificationFeatureBuilderTest {
    @Test
    fun notificationKeyUsesExplicitUserId() {
        assertEquals(
            "12:com.example.chat:42:message",
            NativeNotificationFeatureBuilder.notificationKey(
                packageName = "com.example.chat",
                notificationId = 42,
                tag = "message",
                userId = 12,
            ),
        )
    }
}
