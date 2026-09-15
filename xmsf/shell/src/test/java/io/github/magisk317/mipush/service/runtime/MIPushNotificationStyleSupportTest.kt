package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MIPushNotificationStyleSupportTest {
    @Test
    fun `explicit conversation icon wins over notification icon`() {
        assertEquals(
            "content://sender",
            MIPushNotificationStyleSupport.selectConversationIconUri(
                primary = "content://sender",
                fallback = "content://notification",
            ),
        )
    }

    @Test
    fun `notification icon is used when conversation icon is missing`() {
        assertEquals(
            "content://notification",
            MIPushNotificationStyleSupport.selectConversationIconUri(
                primary = "  ",
                fallback = "content://notification",
            ),
        )
    }

    @Test
    fun `application fallback is allowed only without an effective uri`() {
        assertTrue(MIPushNotificationStyleSupport.shouldUseApplicationIconFallback(null))
        assertTrue(MIPushNotificationStyleSupport.shouldUseApplicationIconFallback("  "))
        assertFalse(MIPushNotificationStyleSupport.shouldUseApplicationIconFallback("content://broken"))
    }
}
