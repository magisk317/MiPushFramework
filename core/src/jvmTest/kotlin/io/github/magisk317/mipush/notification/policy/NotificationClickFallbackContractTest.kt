package io.github.magisk317.mipush.notification.policy

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationClickFallbackContractTest {
    @Test
    fun `launcher fallback extras key stays stable for the systemui proxy`() {
        assertEquals(
            "mipush.click_use_launcher_fallback",
            NotificationClickFallbackContract.USE_LAUNCHER_FALLBACK,
        )
    }
}
