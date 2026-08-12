package io.github.magisk317.mipush.common.notification

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NotificationClickFallbackContractTest {
    @Test
    fun `known broken click packages opt into launcher fallback`() {
        assertTrue(NotificationClickFallbackContract.shouldUseLauncherFallback("com.tencent.mobileqq"))
        assertTrue(NotificationClickFallbackContract.shouldUseLauncherFallback("com.taobao.idlefish"))
    }

    @Test
    fun `normal packages and missing package keep their original pending intent`() {
        assertFalse(NotificationClickFallbackContract.shouldUseLauncherFallback("com.example.chat"))
        assertFalse(
            NotificationClickFallbackContract.shouldUseLauncherFallback(
                "com.example.com.tencent.mobileqq.wrapper",
            ),
        )
        assertFalse(NotificationClickFallbackContract.shouldUseLauncherFallback(null))
    }
}
