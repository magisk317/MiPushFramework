package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SystemUiNotificationPolicyTest {
    @Test
    fun `managed notifications always attempt icon pack substitution`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = false,
                systemAppIconPack = false,
                isMiPushManaged = true,
                uid = 10287,
                isSystemApp = false,
            )
        )
    }

    @Test
    fun `third party apps follow the third-party switch only`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = true,
                systemAppIconPack = false,
                isMiPushManaged = false,
                uid = 10123,
                isSystemApp = false,
            )
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = false,
                systemAppIconPack = true,
                isMiPushManaged = false,
                uid = 10123,
                isSystemApp = false,
            )
        )
    }

    @Test
    fun `system apps and system uids follow the system-app switch`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = false,
                systemAppIconPack = true,
                isMiPushManaged = false,
                uid = 1000,
                isSystemApp = false,
            )
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = true,
                systemAppIconPack = false,
                isMiPushManaged = false,
                uid = 1000,
                isSystemApp = false,
            )
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = false,
                systemAppIconPack = true,
                isMiPushManaged = false,
                uid = 10123,
                isSystemApp = true,
            )
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = true,
                systemAppIconPack = false,
                isMiPushManaged = false,
                uid = 10123,
                isSystemApp = true,
            )
        )
    }

    @Test
    fun `substitution blocking only covers managed posts and pack hits`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                isMiPushManaged = true,
                iconPackHit = false,
            )
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                isMiPushManaged = false,
                iconPackHit = true,
            )
        )
        // A pack miss leaves the native MIUI substitution in place: it is the system's own
        // colored-logo behavior and never paints a white block.
        assertFalse(
            SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                isMiPushManaged = false,
                iconPackHit = false,
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
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIcon(
                isMiPushManaged = false,
                iconPackHit = false,
            )
        )
    }
}
