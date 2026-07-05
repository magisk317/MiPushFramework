package io.github.magisk317.mipush.hook.systemui

import android.os.Bundle
import io.github.magisk317.mipush.hook.island.IslandDispatchContract

internal object SystemUiNotificationPolicy {
    private const val DEFAULT_STATUS_BAR_ICON_TINT = -0x1
    private const val PER_USER_RANGE = 100_000
    private const val FIRST_APPLICATION_UID = 10_000
    private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"

    /** Mirrors [android.graphics.drawable.Icon.TYPE_RESOURCE]. */
    const val ICON_TYPE_RESOURCE = 2

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
        if (forceGlobalStatusBarIcons && !colorStatusBarIcon) return true
        return colorStatusBarIcon && isMiPushManaged
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
    ): Boolean {
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
            )
        ) {
            return false
        }
        return isResourceSmallIconLoadable(iconType, resId, resPackage)
    }

    fun shouldApplySmallIconColor(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
        isGrayscaleIcon: Boolean,
    ): Boolean = colorStatusBarIcon &&
        shouldProcessSmallIconColor(colorStatusBarIcon, forceGlobalStatusBarIcons, isMiPushManaged) &&
        !isGrayscaleIcon

    fun shouldProcessSmallIconColor(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
    ): Boolean = colorStatusBarIcon && shouldHandleStatusBarIcon(
        forceGlobalStatusBarIcons = forceGlobalStatusBarIcons,
        isMiPushManaged = isMiPushManaged,
    )

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

    fun shouldApplyGlobalMonochromeToNotification(
        colorStatusBarIcon: Boolean,
        forceGlobalStatusBarIcons: Boolean,
        isMiPushManaged: Boolean,
        packageName: String?,
        uid: Int,
        isSystemApp: Boolean,
        canColorize: Boolean,
    ): Boolean {
        if (!shouldForceGlobalMonochrome(colorStatusBarIcon, forceGlobalStatusBarIcons)) {
            return false
        }
        if (isMiPushManaged) return true
        if (packageName == SECURITY_CENTER_PACKAGE) return false
        if (isSystemApp || !isApplicationUid(uid)) return false
        if (canColorize) return false
        return true
    }

    fun globalMonochromeTint(requestedColor: Int, fallbackColor: Int): Int {
        return when {
            requestedColor != 0 -> requestedColor
            fallbackColor != 0 -> fallbackColor
            else -> DEFAULT_STATUS_BAR_ICON_TINT
        }
    }

    private fun isApplicationUid(uid: Int): Boolean {
        if (uid < 0) return false
        return uid % PER_USER_RANGE >= FIRST_APPLICATION_UID
    }
}
