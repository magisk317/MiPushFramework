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
        // Brand RGB must not leak through as monochrome SRC_IN tint.
        assertTrue(
            SystemUiNotificationPolicy.globalMonochromeTint(0, 0x123456) ==
                SystemUiNotificationPolicy.globalMonochromeTint(0, 0),
        )
        assertTrue(
            SystemUiNotificationPolicy.globalMonochromeTint(0x654321, 0x123456) ==
                SystemUiNotificationPolicy.globalMonochromeTint(0, 0),
        )
        // Grayscale / white-black system tints stay as-is.
        assertTrue(SystemUiNotificationPolicy.globalMonochromeTint(0xFF888888.toInt(), 0) == 0xFF888888.toInt())
        assertTrue(SystemUiNotificationPolicy.globalMonochromeTint(0, 0xFFFFFFFF.toInt()) == 0xFFFFFFFF.toInt())
        assertTrue(SystemUiNotificationPolicy.isGrayscaleTintColor(0xFF111111.toInt()))
        assertFalse(SystemUiNotificationPolicy.isGrayscaleTintColor(0xFF1678FF.toInt()))
    }

    @Test
    fun `shouldInterceptSmallIcon still selects strong monochrome and color MiPush scopes`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIcon(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = true,
            ),
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIcon(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = false,
            ),
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIcon(
                colorStatusBarIcon = true,
                forceGlobalStatusBarIcons = false,
                isMiPushManaged = true,
            ),
        )
        // Scoped monochrome (global off): still intercept MiPush-managed icons.
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIcon(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = false,
                isMiPushManaged = true,
            ),
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIcon(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = false,
                isMiPushManaged = false,
            ),
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIcon(
                colorStatusBarIcon = true,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = false,
            ),
        )
    }

    @Test
    fun `icon guard keeps monochrome BITMAP and RESOURCE intercept`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = true,
                iconType = SystemUiNotificationPolicy.ICON_TYPE_BITMAP,
                resId = 0,
                resPackage = null,
                packageName = "com.example.app",
                uid = 10123,
                isSystemApp = false,
                canColorize = false,
            ),
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = true,
                iconType = SystemUiNotificationPolicy.ICON_TYPE_RESOURCE,
                resId = 0x7f010001,
                resPackage = "com.xiaomi.xmsf",
                packageName = "com.android.systemui",
                uid = 1000,
                isSystemApp = true,
                canColorize = false,
            ),
        )
    }
}
