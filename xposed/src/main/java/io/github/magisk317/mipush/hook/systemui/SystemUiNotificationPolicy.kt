package io.github.magisk317.mipush.hook.systemui

import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.magisk317.mipush.hook.island.IslandDispatchContract

internal object SystemUiNotificationPolicy {
    private const val DEFAULT_STATUS_BAR_ICON_TINT = -0x1
    private const val PER_USER_RANGE = 100_000
    private const val FIRST_APPLICATION_UID = 10_000
    private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"

    /** Mirrors [android.graphics.drawable.Icon.TYPE_RESOURCE]. */
    const val ICON_TYPE_RESOURCE = 2

    /** Mirrors [android.graphics.drawable.Icon.TYPE_BITMAP]. */
    const val ICON_TYPE_BITMAP = 1

    /** Mirrors [android.app.Notification.FLAG_GROUP_SUMMARY]. */
    const val FLAG_GROUP_SUMMARY = 0x00000200

    /**
     * HyperOS/AOSP auto-group summary glyph
     * (`com.android.internal.R.drawable.ic_notification_summary_auto`).
     */
    const val FRAMEWORK_AUTOGROUP_SUMMARY_ICON_ID = 0x010805a6

    /** Package name that owns framework (android.R) resources. */
    private const val FRAMEWORK_RES_PACKAGE = "android"

    /** High-byte package id segment of a framework resource id (android.R.* == 0x01______). */
    private const val FRAMEWORK_PACKAGE_ID = 0x01

    private const val EXTRA_TARGET_PACKAGE = "target_package"
    private const val EXTRA_MIUI_TARGET_PACKAGE = "miui.targetPkg"
    private const val EXTRA_XMSF_TARGET_PACKAGE = "xmsf_target_package"
    private const val EXTRA_MOCK_REPLAY_RECEIPT = "mipush_mock_replay_receipt"
    private const val EXTRA_MOCK_REPLAY_SOURCE_PACKAGE = "mipush_mock_replay_source_package"

    fun isMiPushManagedNotification(extras: Bundle?): Boolean {
        if (extras == null) return false
        return hasMiPushManagementMarker(
            containsKey = extras::containsKey,
            getBoolean = extras::getBoolean,
            getString = extras::getString,
        )
    }

    fun hasMiPushManagementMarker(
        containsKey: (String) -> Boolean,
        getBoolean: (String, Boolean) -> Boolean,
        getString: (String) -> String?,
    ): Boolean {
        return containsKey(EXTRA_TARGET_PACKAGE) ||
            containsKey(EXTRA_MIUI_TARGET_PACKAGE) ||
            containsKey(EXTRA_XMSF_TARGET_PACKAGE) ||
            getBoolean(EXTRA_MOCK_REPLAY_RECEIPT, false) ||
            getString(EXTRA_MOCK_REPLAY_SOURCE_PACKAGE)?.isNotBlank() == true ||
            getString(IslandDispatchContract.SOURCE_PACKAGE)?.isNotBlank() == true ||
            getString(IslandDispatchContract.OWNER) == IslandDispatchContract.OWNER_MARKER
    }

    fun shouldInterceptSmallIcon(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
    ): Boolean {
        // Strong monochrome: intercept every eligible notification so MIUI cannot swap in
        // multi-color app logos for either RESOURCE or BITMAP small icons.
        if (forceGlobalStatusBarIcons && !colorStatusBarIcon) return true
        // MiPush-managed notifications: intercept for both color and monochrome modes.
        // Monochrome posts white-alpha BITMAP silhouettes; declining getSmallIcon lets HyperOS
        // replace them with the multi-color launcher icon and the status bar stays full-color.
        return isMiPushManaged
    }

    /**
     * Whether a RESOURCE-typed small icon can actually be loaded from its declared package.
     *
     * A malformed icon (e.g. a third-party module that calls
     * `setSmallIcon(android.R.drawable.xxx)` while posting under another app's package) ends up
     * carrying a framework resource id (`0x01______`) tagged with a non-framework `resPackage`.
     * SystemUI then resolves the id against that package's resource table, where it does not
     * exist, and renders a broken glyph.
     *
     * Only RESOURCE icons are inspected; every other icon type (bitmap, uri, data, adaptive) is
     * treated as loadable because it carries its own pixels and cannot suffer this mismatch.
     *
     * @return true when the icon is safe to force into the status bar, false when it should be
     *   left to MIUI's native fallback (which substitutes the posting app's icon).
     */
    fun isResourceSmallIconLoadable(
        iconType: Int,
        resId: Int,
        resPackage: String?,
    ): Boolean {
        if (iconType != ICON_TYPE_RESOURCE) return true
        if (resId == 0) return false
        val packageIdSegment = (resId ushr 24) and 0xff
        val declaredPackage = resPackage?.takeIf { it.isNotBlank() }
        // A framework resource id is only valid when it resolves against the framework package.
        if (packageIdSegment == FRAMEWORK_PACKAGE_ID) {
            return declaredPackage == null || declaredPackage == FRAMEWORK_RES_PACKAGE
        }
        return true
    }

    /** HyperOS AUTOGROUP_SUMMARY often ships RESOURCE smallIcon with resId=0. */
    fun isZeroResourceSmallIcon(iconType: Int, resId: Int): Boolean {
        return iconType == ICON_TYPE_RESOURCE && resId == 0
    }

    fun isGroupSummary(notificationFlags: Int): Boolean {
        return notificationFlags and FLAG_GROUP_SUMMARY != 0
    }

    /**
     * Framework auto-group header icons (e.g. [FRAMEWORK_AUTOGROUP_SUMMARY_ICON_ID]) are not the
     * owning app's status-bar glyph. Forcing them under monochrome looks like a broken/wrong mark.
     */
    fun isFrameworkAutogroupSummaryIcon(
        iconType: Int,
        resId: Int,
        resPackage: String?,
    ): Boolean {
        if (iconType != ICON_TYPE_RESOURCE || resId == 0) return false
        if (resId == FRAMEWORK_AUTOGROUP_SUMMARY_ICON_ID) return true
        val packageIdSegment = (resId ushr 24) and 0xff
        if (packageIdSegment != FRAMEWORK_PACKAGE_ID) return false
        val declaredPackage = resPackage?.takeIf { it.isNotBlank() }
        return declaredPackage == null || declaredPackage == FRAMEWORK_RES_PACKAGE
    }

    /**
     * Whether getSmallIcon should replace a broken/generic RESOURCE icon instead of forcing it.
     *
     * - resId=0 AUTOGROUP → white status-bar block
     * - GROUP_SUMMARY + android autogroup glyph → wrong generic mark (Alipay aggregate case)
     * Framework/package *mismatches* still decline via the normal guard (MIUI substitutes OK).
     */
    fun shouldReplaceBrokenResourceSmallIcon(
        iconType: Int,
        resId: Int,
        resPackage: String? = null,
        notificationFlags: Int = 0,
    ): Boolean {
        if (isZeroResourceSmallIcon(iconType, resId)) return true
        if (!isGroupSummary(notificationFlags)) return false
        return isFrameworkAutogroupSummaryIcon(iconType, resId, resPackage)
    }

    /**
     * Under monochrome, multi-color RESOURCE smallIcons (app logos) should become a white
     * package silhouette rather than relying on SRC_IN over multi-color assets.
     *
     * Already-grayscale status glyphs (e.g. Messaging `stat_notify_sms`) must be kept: replacing
     * them with the launcher white silhouette turns solid adaptive badges into status-bar white
     * blocks. Callers pass [isGrayscaleIcon]=true for those glyphs (or when detection is
     * inconclusive and keeping the original RESOURCE is safer than a launcher square).
     */
    fun shouldReplaceResourceWithPackageMonochrome(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
        iconType: Int,
        packageName: String?,
        uid: Int,
        isSystemApp: Boolean,
        canColorize: Boolean,
        isGrayscaleIcon: Boolean = false,
    ): Boolean {
        if (iconType != ICON_TYPE_RESOURCE) return false
        if (colorStatusBarIcon) return false
        if (isGrayscaleIcon) return false
        return shouldApplyMonochromeTintToNotification(
            colorStatusBarIcon = colorStatusBarIcon,
            forceGlobalStatusBarIcons = forceGlobalStatusBarIcons,
            isMiPushManaged = isMiPushManaged,
            packageName = packageName,
            uid = uid,
            isSystemApp = isSystemApp,
            canColorize = canColorize,
        )
    }

    /**
     * MiPush-managed posts can carry the target app launcher bitmap as `smallIcon`.
     *
     * StatusBarIconView tints that bitmap in monochrome mode; if the launcher asset has an opaque
     * background the status bar becomes a white block. Replace it on the status-bar getSmallIcon
     * path only. The posted Notification object stays untouched so shade/header app icons keep
     * native colored rendering.
     */
    fun shouldReplaceBitmapWithPackageMonochrome(
        colorStatusBarIcon: Boolean,
        isMiPushManaged: Boolean,
        iconType: Int,
    ): Boolean {
        if (iconType != ICON_TYPE_BITMAP) return false
        if (colorStatusBarIcon) return false
        return isMiPushManaged
    }

    /**
     * Intercept decision for the getSmallIcon hook that also guards against forcing a broken
     * RESOURCE icon into the status bar. When the base policy would intercept but the icon is not
     * loadable, we decline so MIUI's native fallback can substitute the posting app's icon.
     */
    fun shouldInterceptSmallIconWithIconGuard(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
        iconType: Int,
        resId: Int,
        resPackage: String?,
        packageName: String?,
        uid: Int,
        isSystemApp: Boolean,
        canColorize: Boolean,
        hasMonochromeResource: Boolean = false,
    ): Boolean {
        // Monochrome BITMAP must still be intercepted. The getSmallIcon hook replaces MiPush
        // launcher bitmaps with a status-bar-only package fallback before this guard; if fallback
        // creation fails, intercepting still prevents MIUI from re-substituting a color app icon.
        if (!shouldInterceptSmallIcon(colorStatusBarIcon, forceGlobalStatusBarIcons, isMiPushManaged)) {
            return false
        }
        if (shouldForceGlobalMonochrome(colorStatusBarIcon, forceGlobalStatusBarIcons) &&
            !shouldApplyGlobalMonochromeToNotification(
                colorStatusBarIcon = colorStatusBarIcon,
                forceGlobalStatusBarIcons = forceGlobalStatusBarIcons,
                isMiPushManaged = isMiPushManaged,
                packageName = packageName,
                uid = uid,
                isSystemApp = isSystemApp,
                canColorize = canColorize,
                hasMonochromeResource = hasMonochromeResource,
            )
        ) {
            return false
        }
        // A successful grayscale check proves that SystemUI can load this RESOURCE even when its
        // framework id is tagged with a system/server package (for example Telecom). In that case
        // keep the posted glyph and tint it instead of declining to MIUI's colored fallback.
        if (iconType == ICON_TYPE_RESOURCE && hasMonochromeResource) return true
        return isResourceSmallIconLoadable(iconType, resId, resPackage)
    }

    enum class StatusBarSmallIconSource {
        FRAMEWORK_NOTIFICATION_SMALL_ICON,
        NATIVE_SYSTEMUI,
    }

    /**
     * Describes the only SystemUI transport used by the icon-pack integration.
     *
     * The publishing side puts the validated bitmap in [android.app.Notification.smallIcon].
     * SystemUI must consume that framework-carried Icon as-is; all existing getSmallIcon guards,
     * substitution decisions and tint/native rendering remain separate policy steps.
     */
    data class StatusBarSmallIconTransport(
        val source: StatusBarSmallIconSource,
        val icon: Icon?,
    ) {
        val entersExistingStatusBarTintPipeline: Boolean
            get() = source == StatusBarSmallIconSource.FRAMEWORK_NOTIFICATION_SMALL_ICON
    }

    fun statusBarSmallIconTransport(
        notificationSmallIcon: Icon?,
        shouldIntercept: Boolean,
    ): StatusBarSmallIconTransport {
        return if (shouldIntercept) {
            StatusBarSmallIconTransport(
                source = StatusBarSmallIconSource.FRAMEWORK_NOTIFICATION_SMALL_ICON,
                icon = notificationSmallIcon,
            )
        } else {
            StatusBarSmallIconTransport(
                source = StatusBarSmallIconSource.NATIVE_SYSTEMUI,
                icon = null,
            )
        }
    }

    fun statusBarIconPreLTagOverride(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
    ): Boolean? {
        if (!shouldHandleStatusBarIcon(forceGlobalStatusBarIcons, isMiPushManaged)) return null
        return if (colorStatusBarIcon) true else forceGlobalStatusBarIcons.takeIf { it }?.let { false }
    }

    private fun shouldHandleStatusBarIcon(
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
    ): Boolean = forceGlobalStatusBarIcons || isMiPushManaged

    fun shouldForceGlobalMonochrome(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
    ): Boolean = forceGlobalStatusBarIcons && !colorStatusBarIcon

    fun shouldApplyMonochromeTintToNotification(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
        packageName: String?,
        uid: Int,
        isSystemApp: Boolean,
        canColorize: Boolean,
        hasMonochromeResource: Boolean = false,
    ): Boolean {
        if (colorStatusBarIcon) return false
        if (isMiPushManaged) return true
        return shouldApplyGlobalMonochromeToNotification(
            colorStatusBarIcon = colorStatusBarIcon,
            forceGlobalStatusBarIcons = forceGlobalStatusBarIcons,
            isMiPushManaged = isMiPushManaged,
            packageName = packageName,
            uid = uid,
            isSystemApp = isSystemApp,
            canColorize = canColorize,
            hasMonochromeResource = hasMonochromeResource,
        )
    }

    fun shouldApplyGlobalMonochromeToNotification(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
        packageName: String?,
        uid: Int,
        isSystemApp: Boolean,
        canColorize: Boolean,
        hasMonochromeResource: Boolean = false,
    ): Boolean {
        if (!shouldForceGlobalMonochrome(colorStatusBarIcon, forceGlobalStatusBarIcons)) {
            return false
        }
        if (isMiPushManaged) return true
        // SecurityCenter keeps OEM colorized glyphs; do not hard-mono it.
        if (packageName == SECURITY_CENTER_PACKAGE) return false
        // Pure system/server uids are eligible only when the posted smallIcon was positively
        // identified as a loadable grayscale resource. Never synthesize a launcher silhouette for
        // them: if no native monochrome glyph exists, preserve the OEM rendering unchanged.
        if (!isApplicationUid(uid)) return hasMonochromeResource
        // Note: do not skip FLAG_CAN_COLORIZE or isSystemApp. HyperOS otherwise keeps multi-color
        // logos on the status bar under "strong monochrome".
        return true
    }

    /**
     * Whether MIUI must NOT substitute the multi-color launcher icon for status-bar rendering.
     *
     * On CN HyperOS, [NotifImageUtil.shouldSubstituteSmallIcon] is true whenever MIUI optimization
     * is on. [StatusBarIconView.updateIconColor] then clears the color filter so BITMAP app logos
     * stay full-color — defeating monochrome even when getSmallIcon is intercepted.
     */
    fun shouldBlockSmallIconSubstitution(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
    ): Boolean {
        if (colorStatusBarIcon) return false
        return isMiPushManaged || forceGlobalStatusBarIcons
    }

    /** Status-bar icon view class whose [updateIconColor] is the only status-bar caller of
     * [NotifImageUtil.shouldSubstituteSmallIcon]. */
    const val STATUS_BAR_ICON_VIEW_CLASS = "com.android.systemui.statusbar.StatusBarIconView"

    /**
     * Whether a [NotifImageUtil.shouldSubstituteSmallIcon] call originates from the status bar.
     *
     * `shouldSubstituteSmallIcon` is called from exactly three sites on CN HyperOS:
     * - `StatusBarIconView.updateIconColor()` — the status-bar path we must force to false so MIUI
     *   does not clear the color filter and re-colorize BITMAP app logos.
     * - `NotificationHeaderViewWrapper.resolveHeaderViews()` and `NotificationViewWrapper.<init>()`
     *   — notification-shade header/expanded rows, which must keep native behavior (colored app
     *   icon via `applyAppIconAllowCustom`).
     *
     * Forcing false on the shade paths is what turns expanded rows / group-summary headers white.
     * We whitelist only the [STATUS_BAR_ICON_VIEW_CLASS] frame so the override stays status-bar only.
     */
    fun isStatusBarSubstitutionContext(stackClassNames: Iterable<String>): Boolean {
        return stackClassNames.any { it == STATUS_BAR_ICON_VIEW_CLASS }
    }

    fun globalMonochromeTint(requestedColor: Int, fallbackColor: Int): Int {
        return when {
            // Keep only grayscale / white-black system tints. Brand RGB colors must not pass
            // through as "monochrome" SRC_IN tints — that is exactly why BITMAP app icons stay
            // full-color on the status bar under strong monochrome mode.
            isGrayscaleTintColor(requestedColor) -> requestedColor
            isGrayscaleTintColor(fallbackColor) -> fallbackColor
            else -> DEFAULT_STATUS_BAR_ICON_TINT
        }
    }

    /**
     * Status-bar monochrome tints are white/black/gray (incl. alpha). Any channel-imbalanced
     * RGB is treated as a brand color and must be replaced by [DEFAULT_STATUS_BAR_ICON_TINT].
     */
    fun isGrayscaleTintColor(color: Int): Boolean {
        if (color == 0) return false
        val r = (color ushr 16) and 0xff
        val g = (color ushr 8) and 0xff
        val b = color and 0xff
        return maxOf(r, g, b) - minOf(r, g, b) <= 8
    }

    private fun isApplicationUid(uid: Int): Boolean {
        if (uid < 0) return false
        return uid % PER_USER_RANGE >= FIRST_APPLICATION_UID
    }
}
