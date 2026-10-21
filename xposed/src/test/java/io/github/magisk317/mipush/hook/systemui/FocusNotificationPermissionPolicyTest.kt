package io.github.magisk317.mipush.hook.systemui

import android.os.Bundle
import io.github.magisk317.mipush.common.XMSF_PACKAGE_NAME
import io.github.magisk317.mipush.common.island.IslandOptions
import io.github.magisk317.mipush.hook.island.IslandPreferences
import io.mockk.every
import io.mockk.mockk
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
    fun `enabled focus authorization bypass is scoped to MiPush identities`() {
        IslandPreferences.resetForTest(
            IslandOptions(
                enabled = true,
                enableFloat = true,
                focusNotification = true,
            )
        )

        assertTrue(FocusNotificationPermissionPolicy.miPushPreferenceAllows(XMSF_PACKAGE_NAME))
        assertTrue(FocusNotificationPermissionPolicy.miPushPreferenceAllows("com.android.systemui"))
        assertFalse(FocusNotificationPermissionPolicy.miPushPreferenceAllows("com.example.unregistered"))
        assertFalse(FocusNotificationPermissionPolicy.miPushPreferenceAllows("com.example.delivery"))
    }

    @Test
    fun `native system app focus remains outside MiPush bypass`() {
        IslandPreferences.resetForTest(
            IslandOptions(
                enabled = true,
                enableFloat = true,
                focusNotification = true,
            )
        )

        val proxyExtras = mockk<Bundle>()
        every { proxyExtras.containsKey(any()) } returns false
        every { proxyExtras.getBoolean(any(), any()) } returns false
        every { proxyExtras.getString(any()) } returns null
        every { proxyExtras.getString("mipush_island_source_pkg") } returns "com.example.source"

        assertFalse(
            FocusNotificationPermissionPolicy.isMiPushFocusNotification(
                packageName = "com.android.mms",
                extras = null,
            )
        )
        assertTrue(
            FocusNotificationPermissionPolicy.isMiPushFocusNotification(
                packageName = XMSF_PACKAGE_NAME,
                extras = null,
            )
        )
        assertTrue(
            FocusNotificationPermissionPolicy.isMiPushFocusNotification(
                packageName = "com.android.systemui",
                extras = proxyExtras,
            )
        )
    }

    @Test
    fun `enabled bypass widens Android 17 focus states to allowed`() {
        IslandPreferences.resetForTest(
            IslandOptions(
                enabled = true,
                enableFloat = true,
                focusNotification = true,
            )
        )

        assertTrue(FocusNotificationPermissionPolicy.mergeState(-1, true) == 1)
        assertTrue(FocusNotificationPermissionPolicy.mergeState(0, true) == 1)
        assertTrue(FocusNotificationPermissionPolicy.mergeState(1, true) == 1)
    }

    @Test
    fun `disabled bypass preserves Android 17 focus states`() {
        assertTrue(FocusNotificationPermissionPolicy.mergeState(-1, false) == -1)
        assertTrue(FocusNotificationPermissionPolicy.mergeState(0, false) == 0)
        assertTrue(FocusNotificationPermissionPolicy.mergeState(1, false) == 1)
    }

    @Test
    fun `disabled focus authorization bypass does not widen any package`() {
        assertFalse(FocusNotificationPermissionPolicy.miPushPreferenceAllows("com.example.any"))
    }

    @Test
    fun `icon-pack category switches gate substitution attempts`() {
        // Managed posts always attempt regardless of switches.
        assertTrue(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = false,
                systemAppIconPack = false,
                isMiPushManaged = true,
                uid = 10123,
                isSystemApp = false,
            )
        )
        // Application uid follows the third-party switch.
        assertFalse(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = false,
                systemAppIconPack = true,
                isMiPushManaged = false,
                uid = 10123,
                isSystemApp = false,
            )
        )
        // System uid follows the system-app switch.
        assertTrue(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = false,
                systemAppIconPack = true,
                isMiPushManaged = false,
                uid = 1000,
                isSystemApp = false,
            )
        )
    }

    @Test
    fun `legacy interception requires managed or a pack hit`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIcon(
                isMiPushManaged = true,
                iconPackHit = false,
            )
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIcon(
                isMiPushManaged = false,
                iconPackHit = true,
            )
        )
        // Pack miss and unmanaged: stay on native rendering.
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIcon(
                isMiPushManaged = false,
                iconPackHit = false,
            )
        )
    }

    @Test
    fun `icon guard keeps managed and pack-hit intercepts`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                isMiPushManaged = true,
                iconPackHit = false,
                iconType = SystemUiNotificationPolicy.ICON_TYPE_BITMAP,
                resId = 0,
                resPackage = null,
            ),
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                isMiPushManaged = false,
                iconPackHit = true,
                iconType = SystemUiNotificationPolicy.ICON_TYPE_RESOURCE,
                resId = 0x7f010001,
                resPackage = "com.xiaomi.xmsf",
            ),
        )
        // Pack miss and unmanaged: never intercept.
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                isMiPushManaged = false,
                iconPackHit = false,
                iconType = SystemUiNotificationPolicy.ICON_TYPE_RESOURCE,
                resId = 0x7f010001,
                resPackage = "com.xiaomi.xmsf",
            ),
        )
    }
}
