package io.github.magisk317.mipush.hook.systemui

import net.jqwik.api.Arbitraries
import net.jqwik.api.Arbitrary
import net.jqwik.api.Combinators
import net.jqwik.api.ForAll
import net.jqwik.api.Property
import net.jqwik.api.Provide
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Resource Small Icon Guard Tests
 *
 * Covers [SystemUiNotificationPolicy.isResourceSmallIconLoadable] and
 * [SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard].
 *
 * Background: a third-party module (e.g. `fansirsqi.xposed.sesame`) posts a notification under
 * another app's package (`com.eg.android.AlipayGphone`) but sets a framework small icon via
 * `setSmallIcon(android.R.drawable.xxx)`. The resulting Icon carries a framework resource id
 * (`0x0108____`) tagged with `resPackage=com.eg.android.AlipayGphone`, which cannot be resolved
 * against Alipay's resource table and renders as a broken glyph.
 *
 * In color mode the getSmallIcon hook does not intercept, so MIUI's native fallback substitutes
 * the posting app's icon and the glyph looks correct. In strong monochrome mode the hook forces
 * `notification.smallIcon` into the status bar, exposing the broken framework/package mismatch as
 * a monochrome broken glyph.
 *
 * The guard declines to intercept when a RESOURCE icon's id/package pairing is unloadable, letting
 * MIUI's native fallback run in monochrome mode too.
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
        private const val XMSF_PACKAGE = "com.xiaomi.xmsf"

        // Framework resource id segment (android.R.drawable.ic_dialog_info == 0x0108009b).
        private const val FRAMEWORK_INFO_RES_ID = 0x0108009b
        // Another framework id observed in the field (the Alipay/sesame broken icon).
        private const val FRAMEWORK_ALIPAY_BROKEN_RES_ID = 0x01080093
        // A typical app resource id segment (0x7f______).
        private const val APP_RES_ID = 0x7f081c55
        // The MiPush island dispatcher icon id observed after the framework-icon fallback fix.
        private const val MIPUSH_PROXY_RES_ID = 0x7f0e0000
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

    // ─── shouldInterceptSmallIconWithIconGuard ───────────────────────────────────

    @Test
    fun `strong monochrome declines to intercept a broken framework icon`() {
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = false,
                iconType = ICON_TYPE_RESOURCE,
                resId = FRAMEWORK_ALIPAY_BROKEN_RES_ID,
                resPackage = ALIPAY_PACKAGE,
                packageName = ALIPAY_PACKAGE,
                uid = USER_APP_UID,
                isSystemApp = false,
                canColorize = false,
            ),
            "Strong monochrome mode must not force a broken framework/package icon into the status " +
                "bar; it should defer to MIUI's native fallback.",
        )
    }

    @Test
    fun `strong monochrome still intercepts a valid third party icon`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = false,
                iconType = ICON_TYPE_RESOURCE,
                resId = APP_RES_ID,
                resPackage = FLCLASH_PACKAGE,
                packageName = FLCLASH_PACKAGE,
                uid = USER_APP_UID,
                isSystemApp = false,
                canColorize = false,
            ),
        )
    }

    @Test
    fun `malformed alipay icon still allows tinting the fallback drawable`() {
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = false,
                iconType = ICON_TYPE_RESOURCE,
                resId = FRAMEWORK_ALIPAY_BROKEN_RES_ID,
                resPackage = ALIPAY_PACKAGE,
                packageName = ALIPAY_PACKAGE,
                uid = USER_APP_UID,
                isSystemApp = false,
                canColorize = false,
            ),
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldApplyGlobalMonochromeToNotification(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = false,
                packageName = ALIPAY_PACKAGE,
                uid = USER_APP_UID,
                isSystemApp = false,
                canColorize = false,
            ),
            "The getSmallIcon hook should defer malformed Sesame/Alipay icons to MIUI fallback, " +
                "but the final fallback drawable still belongs to a user app and should be tinted.",
        )
    }

    @Test
    fun `strong monochrome skips security center colorized system icon`() {
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = false,
                iconType = ICON_TYPE_RESOURCE,
                resId = APP_RES_ID,
                resPackage = SECURITY_CENTER_PACKAGE,
                packageName = SECURITY_CENTER_PACKAGE,
                uid = SYSTEM_UID,
                isSystemApp = true,
                canColorize = true,
            ),
        )
        assertFalse(
            SystemUiNotificationPolicy.shouldApplyGlobalMonochromeToNotification(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = false,
                packageName = SECURITY_CENTER_PACKAGE,
                uid = SYSTEM_UID,
                isSystemApp = true,
                canColorize = true,
            ),
            "SecurityCenter uses MIUI/system colorized notification resources; strong mode should not hard-fix it.",
        )
    }

    @Test
    fun `strong monochrome keeps MiPush island proxy eligible even when posted by SystemUI`() {
        assertTrue(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = true,
                iconType = ICON_TYPE_RESOURCE,
                resId = MIPUSH_PROXY_RES_ID,
                resPackage = XMSF_PACKAGE,
                packageName = SYSTEMUI_PACKAGE,
                uid = SYSTEM_UID,
                isSystemApp = true,
                canColorize = false,
            ),
        )
        assertTrue(
            SystemUiNotificationPolicy.shouldApplyGlobalMonochromeToNotification(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = true,
                packageName = SYSTEMUI_PACKAGE,
                uid = SYSTEM_UID,
                isSystemApp = true,
                canColorize = false,
            ),
        )
    }

    @Test
    fun `guard never intercepts when the base policy declines`() {
        // Base policy declines: color mode ON but not MiPush-managed and not global.
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = true,
                forceGlobalStatusBarIcons = false,
                isMiPushManaged = false,
                iconType = ICON_TYPE_RESOURCE,
                resId = APP_RES_ID,
                resPackage = FLCLASH_PACKAGE,
                packageName = FLCLASH_PACKAGE,
                uid = USER_APP_UID,
                isSystemApp = false,
                canColorize = false,
            ),
        )
    }

    // ─── Property: guard only ever narrows the base decision ─────────────────────

    @Provide
    fun guardInputs(): Arbitrary<GuardInput> = Combinators.combine(
        Arbitraries.of(true, false),
        Arbitraries.of(true, false),
        Arbitraries.of(true, false),
        Arbitraries.of(ICON_TYPE_RESOURCE, ICON_TYPE_BITMAP, ICON_TYPE_URI, -1),
        Arbitraries.of(0, FRAMEWORK_INFO_RES_ID, FRAMEWORK_ALIPAY_BROKEN_RES_ID, APP_RES_ID),
        Arbitraries.of("android", "com.android.systemui", "com.eg.android.AlipayGphone", ""),
    ).`as` { color, global, managed, type, resId, pkg ->
        GuardInput(color, global, managed, type, resId, pkg)
    }

    data class GuardInput(
        val colorStatusBarIcon: Boolean,
        val forceGlobalStatusBarIcons: Boolean,
        val isMiPushManaged: Boolean,
        val iconType: Int,
        val resId: Int,
        val resPackage: String,
    )

    @Property(tries = 300)
    fun `guarded intercept implies base intercept`(
        @ForAll("guardInputs") input: GuardInput,
    ) {
        val base = SystemUiNotificationPolicy.shouldInterceptSmallIcon(
            colorStatusBarIcon = input.colorStatusBarIcon,
            forceGlobalStatusBarIcons = input.forceGlobalStatusBarIcons,
            isMiPushManaged = input.isMiPushManaged,
        )
        val guarded = SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
            colorStatusBarIcon = input.colorStatusBarIcon,
            forceGlobalStatusBarIcons = input.forceGlobalStatusBarIcons,
            isMiPushManaged = input.isMiPushManaged,
            iconType = input.iconType,
            resId = input.resId,
            resPackage = input.resPackage,
            packageName = FLCLASH_PACKAGE,
            uid = USER_APP_UID,
            isSystemApp = false,
            canColorize = false,
        )
        // The guard may only turn a true into false (decline a broken icon), never the reverse.
        if (guarded) {
            assertTrue(base, "Guard intercepted while base policy declined: $input")
        }
    }

    @Property(tries = 300)
    fun `guard declines exactly when base intercepts an unloadable icon`(
        @ForAll("guardInputs") input: GuardInput,
    ) {
        val base = SystemUiNotificationPolicy.shouldInterceptSmallIcon(
            colorStatusBarIcon = input.colorStatusBarIcon,
            forceGlobalStatusBarIcons = input.forceGlobalStatusBarIcons,
            isMiPushManaged = input.isMiPushManaged,
        )
        val loadable = SystemUiNotificationPolicy.isResourceSmallIconLoadable(
            iconType = input.iconType,
            resId = input.resId,
            resPackage = input.resPackage,
        )
        val guarded = SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
            colorStatusBarIcon = input.colorStatusBarIcon,
            forceGlobalStatusBarIcons = input.forceGlobalStatusBarIcons,
            isMiPushManaged = input.isMiPushManaged,
            iconType = input.iconType,
            resId = input.resId,
            resPackage = input.resPackage,
            packageName = FLCLASH_PACKAGE,
            uid = USER_APP_UID,
            isSystemApp = false,
            canColorize = false,
        )
        val monochromeBitmapDeclined =
            !input.colorStatusBarIcon && input.iconType == ICON_TYPE_BITMAP
        assertTrue(
            guarded == (base && loadable && !monochromeBitmapDeclined),
            "Guard must equal base AND loadable, except monochrome BITMAP which is always declined: $input",
        )
    }

    @Test
    fun `monochrome declines BITMAP intercept even for MiPush managed icons`() {
        assertFalse(
            SystemUiNotificationPolicy.shouldInterceptSmallIconWithIconGuard(
                colorStatusBarIcon = false,
                forceGlobalStatusBarIcons = true,
                isMiPushManaged = true,
                iconType = ICON_TYPE_BITMAP,
                resId = 0,
                resPackage = null,
                packageName = XMSF_PACKAGE,
                uid = USER_APP_UID,
                isSystemApp = false,
                canColorize = false,
            ),
        )
    }
}
