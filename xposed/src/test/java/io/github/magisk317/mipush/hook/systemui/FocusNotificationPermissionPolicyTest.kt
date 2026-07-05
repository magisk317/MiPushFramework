package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class FocusNotificationPermissionPolicyTest {
    @Test
    fun `system allowed focus is never narrowed by MiPush preference`() {
        assertTrue(
            FocusNotificationPermissionPolicy.merge(
                systemAllowed = true,
                miPushAllowed = false,
            )
        )
    }

    @Test
    fun `MiPush preference can only widen denied focus permission`() {
        assertTrue(
            FocusNotificationPermissionPolicy.merge(
                systemAllowed = false,
                miPushAllowed = true,
            )
        )
        assertFalse(
            FocusNotificationPermissionPolicy.merge(
                systemAllowed = false,
                miPushAllowed = false,
            )
        )
    }

    @Test
    fun `strong global monochrome uses a nonzero tint fallback`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldForceGlobalMonochrome(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
            )
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldForceGlobalMonochrome(
                colorStatusBarIcon = true,
                forceGlobalStatusBarIcons = true,
            )
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldForceGlobalMonochrome(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = false,
            )
        )

        assertTrue(SystemUiNotificationPolicy.globalMonochromeTint(0, 0) != 0)
        assertTrue(SystemUiNotificationPolicy.globalMonochromeTint(0, 0x123456) == 0x123456)
        assertTrue(SystemUiNotificationPolicy.globalMonochromeTint(0x654321, 0x123456) == 0x654321)
    }
}
