package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemUiNotificationPolicyTest {
    @Test
    fun `monochrome resource skips forced tint even when descriptor was substituted`() {
        assertFalse(
            SystemUiNotificationPolicy.shouldTintStatusBarGlyph(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = false,
                packageName = "com.android.chrome",
                uid = 10287,
                isSystemApp = false,
                canColorize = false,
                hasSubstitutedGlyph = true,
                hasMonochromeResource = true,
            )
        )
    }

    @Test
    fun `substituted glyph without monochrome resource remains tinted`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldTintStatusBarGlyph(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = false,
                packageName = "com.example.app",
                uid = 10123,
                isSystemApp = false,
                canColorize = false,
                hasSubstitutedGlyph = true,
                hasMonochromeResource = false,
            )
        )
    }
}
