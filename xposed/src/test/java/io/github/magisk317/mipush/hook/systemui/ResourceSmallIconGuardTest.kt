package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Tests for the 2026-10 icon policy: "icon pack or native rendering, never a white block".
 *
 * The module substitutes an icon-pack glyph only when the owner's category switch allows it
 * AND the pack covers the app; everything else stays on SystemUI's native rendering, which is
 * the verified white-block-free baseline. The module never authors a tint.
 */
class ResourceSmallIconGuardTest {
    companion object {
        private const val ICON_TYPE_RESOURCE = SystemUiNotificationPolicy.ICON_TYPE_RESOURCE
        private const val ICON_TYPE_BITMAP = 1
        private const val ICON_TYPE_URI = 4
        private const val ICON_TYPE_ADAPTIVE_BITMAP = 5

        private const val USER_APP_UID = 10_384
        private const val SYSTEM_UID = 1_000
        private const val FLCLASH_PACKAGE = "com.follow.clash"
        private const val ALIPAY_PACKAGE = "com.eg.android.AlipayGphone"
        private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
        private const val SYSTEMUI_PACKAGE = "com.android.systemui"

        private const val FRAMEWORK_INFO_RES_ID = 0x0108009b
        private const val FRAMEWORK_ALIPAY_BROKEN_RES_ID = 0x01080093
        private const val FRAMEWORK_AUTOGROUP_SUMMARY_ICON_ID = 0x010805c7
        private const val APP_RES_ID = 0x7f081c55
        private const val FLAG_GROUP_SUMMARY = 0x00000200
        private const val FLAG_AUTOGROUP_SUMMARY = 0x00000400
    }

    // ─── isResourceSmallIconLoadable ─────────────────────────────────────────────

    @Test
    fun `framework res id tagged with a non-framework package is not loadable`() {
        assertFalse(
            SystemUiNotificationPolicy.isResourceSmallIconLoadable(
                iconType = ICON_TYPE_RESOURCE,
                resId = FRAMEWORK_ALIPAY_BROKEN_RES_ID,
                resPackage = "com.eg.android.AlipayGphone",
            ),
            "A framework resource id declared under a third-party package cannot be resolved and " +
                "must be treated as not loadable.",
        )
    }

    @Test
    fun `framework res id tagged with the framework package is loadable`() {
        assertTrue(
            SystemUiNotificationPolicy.isResourceSmallIconLoadable(
                iconType = ICON_TYPE_RESOURCE,
                resId = FRAMEWORK_INFO_RES_ID,
                resPackage = "android",
            ),
        )
    }

    @Test
    fun `framework res id with no declared package is loadable`() {
        // A null/blank resPackage resolves against the framework, which owns 0x01 ids.
        assertTrue(
            SystemUiNotificationPolicy.isResourceSmallIconLoadable(
                iconType = ICON_TYPE_RESOURCE,
                resId = FRAMEWORK_INFO_RES_ID,
                resPackage = null,
            ),
        )
        assertTrue(
            SystemUiNotificationPolicy.isResourceSmallIconLoadable(
                iconType = ICON_TYPE_RESOURCE,
                resId = FRAMEWORK_INFO_RES_ID,
                resPackage = "",
            ),
        )
    }

    @Test
    fun `app res id tagged with an app package is loadable`() {
        assertTrue(
            SystemUiNotificationPolicy.isResourceSmallIconLoadable(
                iconType = ICON_TYPE_RESOURCE,
                resId = APP_RES_ID,
                resPackage = SYSTEMUI_PACKAGE,
            ),
        )
    }

    @Test
    fun `app res id with no declared package is loadable`() {
        assertTrue(
            SystemUiNotificationPolicy.isResourceSmallIconLoadable(
                iconType = ICON_TYPE_RESOURCE,
                resId = APP_RES_ID,
                resPackage = null,
            ),
        )
    }

    @Test
    fun `zero res id is not loadable`() {
        assertFalse(
            SystemUiNotificationPolicy.isResourceSmallIconLoadable(
                iconType = ICON_TYPE_RESOURCE,
                resId = 0,
                resPackage = "com.android.systemui",
            ),
        )
    }

    @Test
    fun `non-resource icon types are always loadable`() {
        for (type in intArrayOf(ICON_TYPE_BITMAP, ICON_TYPE_URI, ICON_TYPE_ADAPTIVE_BITMAP, -1)) {
            assertTrue(
                SystemUiNotificationPolicy.isResourceSmallIconLoadable(
                    iconType = type,
                    resId = 0,
                    resPackage = "com.eg.android.AlipayGphone",
                ),
                "Icon type $type carries its own pixels and cannot suffer a res/package mismatch.",
            )
        }
    }

    // ─── shouldAttemptIconPackSubstitution ───────────────────────────────────────

    @Test
    fun `managed notifications always attempt the pack`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = false,
                systemAppIconPack = false,
                isMiPushManaged = true,
                uid = USER_APP_UID,
                isSystemApp = false,
            ),
        )
    }

    @Test
    fun `third party and system categories map to their own switches`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = true,
                systemAppIconPack = false,
                isMiPushManaged = false,
                uid = USER_APP_UID,
                isSystemApp = false,
            ),
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = false,
                systemAppIconPack = true,
                isMiPushManaged = false,
                uid = USER_APP_UID,
                isSystemApp = false,
            ),
        )
        // System uid / system package follow the system-app switch.
        assertTrue(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = false,
                systemAppIconPack = true,
                isMiPushManaged = false,
                uid = SYSTEM_UID,
                isSystemApp = true,
            ),
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldAttemptIconPackSubstitution(
                thirdPartyIconPack = true,
                systemAppIconPack = false,
                isMiPushManaged = false,
                uid = SYSTEM_UID,
                isSystemApp = true,
            ),
        )
    }

    // ─── shouldReplaceResourceWithPackageMonochrome ──────────────────────────────

    @Test
    fun `multi-color RESOURCE swap is category gated`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldReplaceResourceWithPackageMonochrome(
                attemptsIconPack = true,
                isMiPushManaged = false,
                iconType = ICON_TYPE_RESOURCE,
                isGrayscaleIcon = false,
            ),
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldReplaceResourceWithPackageMonochrome(
                attemptsIconPack = false,
                isMiPushManaged = false,
                iconType = ICON_TYPE_RESOURCE,
                isGrayscaleIcon = false,
            ),
        )
    }

    @Test
    fun `grayscale glyphs and non-resource icons are never swapped`() {
        assertFalse(
            SystemUiNotificationPolicy.shouldReplaceResourceWithPackageMonochrome(
                attemptsIconPack = true,
                isMiPushManaged = true,
                iconType = ICON_TYPE_RESOURCE,
                isGrayscaleIcon = true,
            ),
            "Grayscale status glyphs are already status-bar ready; a silhouette swap is lossy.",
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldReplaceResourceWithPackageMonochrome(
                attemptsIconPack = true,
                isMiPushManaged = true,
                iconType = ICON_TYPE_BITMAP,
                isGrayscaleIcon = false,
            ),
        )
    }

    // ─── shouldReplaceBrokenResourceSmallIcon ────────────────────────────────────

    @Test
    fun `zero resource small icon is replaced`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldReplaceBrokenResourceSmallIcon(
                iconType = ICON_TYPE_RESOURCE,
                resId = 0,
                resPackage = null,
                notificationFlags = 0,
            ),
        )
    }

    @Test
    fun `autogroup summary framework glyph is replaced for group headers`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldReplaceBrokenResourceSmallIcon(
                iconType = ICON_TYPE_RESOURCE,
                resId = FRAMEWORK_AUTOGROUP_SUMMARY_ICON_ID,
                resPackage = null,
                notificationFlags = FLAG_GROUP_SUMMARY or FLAG_AUTOGROUP_SUMMARY,
            ),
        )
    }

    @Test
    fun `ordinary group summaries are not treated as broken`() {
        assertFalse(
            SystemUiNotificationPolicy.shouldReplaceBrokenResourceSmallIcon(
                iconType = ICON_TYPE_RESOURCE,
                resId = APP_RES_ID,
                resPackage = FLCLASH_PACKAGE,
                notificationFlags = FLAG_GROUP_SUMMARY,
            ),
        )
    }

    // ─── shouldReplaceBitmapWithPackageMonochrome ────────────────────────────────

    @Test
    fun `managed BITMAP is replaced in monochrome mode`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldReplaceBitmapWithPackageMonochrome(
                isMiPushManaged = true,
                iconType = ICON_TYPE_BITMAP,
            ),
            "Opaque launcher bitmaps tint into white blocks; the silhouette must take over.",
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldReplaceBitmapWithPackageMonochrome(
                isMiPushManaged = false,
                iconType = ICON_TYPE_BITMAP,
            ),
        )
    }

    // ─── shouldInterceptSmallIconWithIconGuard ───────────────────────────────────

    @Test
    fun `guard declines when neither managed nor a pack hit`() {
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                isMiPushManaged = false,
                iconPackHit = false,
                iconType = ICON_TYPE_RESOURCE,
                resId = APP_RES_ID,
                resPackage = FLCLASH_PACKAGE,
            ),
            "A pack miss must stay on the native renderer — never force the posted glyph.",
        )
    }

    @Test
    fun `guard declines a broken framework icon even on a hit`() {
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                isMiPushManaged = false,
                iconPackHit = true,
                iconType = ICON_TYPE_RESOURCE,
                resId = FRAMEWORK_ALIPAY_BROKEN_RES_ID,
                resPackage = ALIPAY_PACKAGE,
            ),
            "Forcing a broken framework/package icon renders garbage; defer to MIUI's fallback.",
        )
    }

    @Test
    fun `guard keeps managed BITMAP and pack-hit RESOURCE intercepts`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                isMiPushManaged = true,
                iconPackHit = false,
                iconType = ICON_TYPE_BITMAP,
                resId = 0,
                resPackage = null,
            ),
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                isMiPushManaged = false,
                iconPackHit = true,
                iconType = ICON_TYPE_RESOURCE,
                resId = APP_RES_ID,
                resPackage = FLCLASH_PACKAGE,
            ),
        )
    }

    @Test
    fun `proven monochrome resource keeps the intercept even with a package mismatch`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                isMiPushManaged = true,
                iconPackHit = false,
                iconType = ICON_TYPE_RESOURCE,
                resId = 0x0108007f,
                resPackage = "com.android.server.telecom",
                hasMonochromeResource = true,
            ),
            "Successful grayscale detection also proves the system resource is loadable.",
        )
    }

    // ─── shouldBlockSmallIconSubstitution ────────────────────────────────────────

    @Test
    fun `MIUI substitution blocking only covers managed posts and pack hits`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                isMiPushManaged = true,
                iconPackHit = false,
            ),
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                isMiPushManaged = false,
                iconPackHit = true,
            ),
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldBlockSmallIconSubstitution(
                isMiPushManaged = false,
                iconPackHit = false,
            ),
            "Pack miss: the native colored-logo substitution IS the product behavior.",
        )
    }

    // ─── statusBarSmallIconTransport ─────────────────────────────────────────────

    @Test
    fun `transport carries the posted glyph only when intercepting`() {
        val intercepted = SystemUiNotificationPolicy.statusBarSmallIconTransport(
            notificationSmallIcon = null,
            shouldIntercept = true,
        )
        assertTrue(
            intercepted.source ==
                SystemUiNotificationPolicy.StatusBarSmallIconSource.FRAMEWORK_NOTIFICATION_SMALL_ICON,
        )
        val native = SystemUiNotificationPolicy.statusBarSmallIconTransport(
            notificationSmallIcon = null,
            shouldIntercept = false,
        )
        assertTrue(
            native.source == SystemUiNotificationPolicy.StatusBarSmallIconSource.NATIVE_SYSTEMUI,
        )
    }

    // ─── statusBarIconPreLTagOverride ────────────────────────────────────────────

    @Test
    fun `pre-L tag marks monochrome participation and passes others through`() {
        assertTrue(
            SystemUiNotificationPolicy.statusBarIconPreLTagOverride(
                attemptsIconPack = false,
                isMiPushManaged = true,
            ) == false,
        )
        assertTrue(
            SystemUiNotificationPolicy.statusBarIconPreLTagOverride(
                attemptsIconPack = true,
                isMiPushManaged = false,
            ) == false,
        )
        assertTrue(
            SystemUiNotificationPolicy.statusBarIconPreLTagOverride(
                attemptsIconPack = false,
                isMiPushManaged = false,
            ) == null,
        )
    }
}
