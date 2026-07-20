package io.github.magisk317.mipush.hook.systemui

import io.github.magisk317.mipush.common.island.IslandOptions
import io.github.magisk317.mipush.hook.island.IslandPreferences
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FocusNotificationPermissionPolicyTest {
    @BeforeEach
    fun reset() {
        IslandPreferences.resetForTest()
    }

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
    fun `enabled focus authorization bypass applies globally`() {
        IslandPreferences.resetForTest(
            IslandOptions(
                enabled = true,
                enableFloat = true,
                focusNotification = true,
            )
        )

        assertTrue(FocusNotificationPermissionPolicy.miPushPreferenceAllows("com.example.unregistered"))
        assertTrue(FocusNotificationPermissionPolicy.miPushPreferenceAllows("com.autonavi.minimap"))
    }

    @Test
    fun `disabled focus authorization bypass does not widen any package`() {
        assertFalse(FocusNotificationPermissionPolicy.miPushPreferenceAllows("com.example.any"))
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
