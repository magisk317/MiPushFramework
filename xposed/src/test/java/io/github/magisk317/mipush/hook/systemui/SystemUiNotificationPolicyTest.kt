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
    fun `synthetic autogroup summaries are identified by flags alone`() {
        assertTrue(
            SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(
                SystemUiNotificationPolicy.FLAG_GROUP_SUMMARY or
                    SystemUiNotificationPolicy.FLAG_AUTOGROUP_SUMMARY,
            )
        )
        assertFalse(
            SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(
                SystemUiNotificationPolicy.FLAG_GROUP_SUMMARY,
            ),
            "An app-authored group summary is not a synthetic autogroup record.",
        )
        assertFalse(
            SystemUiNotificationPolicy.isSyntheticAutogroupSummaryNotification(
                SystemUiNotificationPolicy.FLAG_AUTOGROUP_SUMMARY,
            ),
        )
    }

    @Test
    fun `monochrome synthetic summaries block MIUI substitution`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                isMiPushManaged = false,
                iconPackHit = false,
                isSyntheticAutogroupSummary = true,
                colorStatusBarIcon = false,
            ),
            "The summary copies a colorful launcher icon but loses target_package.",
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                isMiPushManaged = false,
                iconPackHit = false,
                isSyntheticAutogroupSummary = true,
                colorStatusBarIcon = true,
            ),
            "Color mode keeps the native colorful summary icon.",
        )
    }

    @Test
    fun `only third-party posts are inspected for a self-supplied monochrome glyph`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                colorStatusBarIcon = false,
                thirdPartyIconPack = true,
                isMiPushManaged = false,
                uid = 10287,
                isSystemApp = false,
            )
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                colorStatusBarIcon = true,
                thirdPartyIconPack = true,
                isMiPushManaged = false,
                uid = 10287,
                isSystemApp = false,
            ),
            "Color mode keeps the native colorful launcher logo.",
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                colorStatusBarIcon = false,
                thirdPartyIconPack = false,
                isMiPushManaged = false,
                uid = 10287,
                isSystemApp = false,
            ),
            "The third-party switch owns this behavior.",
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                colorStatusBarIcon = false,
                thirdPartyIconPack = true,
                isMiPushManaged = true,
                uid = 10287,
                isSystemApp = false,
            ),
            "MiPush-managed posts already own their status-bar asset.",
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                colorStatusBarIcon = false,
                thirdPartyIconPack = true,
                isMiPushManaged = false,
                uid = 10123,
                isSystemApp = true,
            ),
            "System apps participate only through the system-app icon-pack switch.",
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldInspectPostedGlyphForMonochrome(
                colorStatusBarIcon = false,
                thirdPartyIconPack = true,
                isMiPushManaged = false,
                uid = 1000,
                isSystemApp = false,
            ),
        )
    }

    @Test
    fun `a self-supplied monochrome glyph blocks substitution without a pack hit`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                isMiPushManaged = false,
                iconPackHit = false,
                keepsOwnMonochromeGlyph = true,
            ),
            "SystemUI tints the app's own grayscale glyph; the colorful substitution is a downgrade.",
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                isMiPushManaged = false,
                iconPackHit = false,
                keepsOwnMonochromeGlyph = false,
            ),
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
