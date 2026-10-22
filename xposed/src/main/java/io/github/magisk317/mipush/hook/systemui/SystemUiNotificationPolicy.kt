package io.github.magisk317.mipush.hook.systemui

import android.graphics.drawable.Icon
import android.os.Bundle
import io.github.magisk317.mipush.common.island.IslandVisualContract
import io.github.magisk317.mipush.hook.island.IslandDispatchContract

internal object SystemUiNotificationPolicy {
    private const val PER_USER_RANGE = 100_000
    private const val FIRST_APPLICATION_UID = 10_000

    /** Mirrors [android.graphics.drawable.Icon.TYPE_RESOURCE]. */
    const val ICON_TYPE_RESOURCE = 2

    /** Mirrors [android.graphics.drawable.Icon.TYPE_BITMAP]. */
    const val ICON_TYPE_BITMAP = 1

    /** Mirrors [android.app.Notification.FLAG_GROUP_SUMMARY]. */
    const val FLAG_GROUP_SUMMARY = 0x00000200

    /** Mirrors the hidden [android.app.Notification.FLAG_AUTOGROUP_SUMMARY]. */
    const val FLAG_AUTOGROUP_SUMMARY = 0x00000400

    /**
     * HyperOS/AOSP auto-group summary glyph
     * (`com.android.internal.R.drawable.ic_notification_summary_auto`).
     */
    const val FRAMEWORK_AUTOGROUP_SUMMARY_ICON_ID = 0x010805c7

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
        // XMSF writes target_package for its managed notifications. Keep this legacy marker for
        // compatibility with the existing MiPush notification contract; strong mode separately
        // handles notifications without any MiPush marker.
        return containsKey(EXTRA_TARGET_PACKAGE) ||
            containsKey(EXTRA_MIUI_TARGET_PACKAGE) ||
            containsKey(EXTRA_XMSF_TARGET_PACKAGE) ||
            getBoolean(EXTRA_MOCK_REPLAY_RECEIPT, false) ||
            getString(EXTRA_MOCK_REPLAY_SOURCE_PACKAGE)?.isNotBlank() == true ||
            getString(IslandDispatchContract.SOURCE_PACKAGE)?.isNotBlank() == true ||
            getString(IslandVisualContract.OWNER_KEY) == IslandVisualContract.MIPUSH_OWNER
    }

    /**
     * Whether the legacy getSmallIcon hook should intercept at all. Product rule (2026-10):
     * icon-pack hit or MiPush-managed only — everything else stays on SystemUI's native
     * rendering, which is the verified white-block-free baseline.
     */
    fun shouldInterceptSmallIcon(
        isMiPushManaged: Boolean,
        iconPackHit: Boolean,
    ): Boolean = isMiPushManaged || iconPackHit

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
        if (iconType != ICON_TYPE_RESOURCE || resId != FRAMEWORK_AUTOGROUP_SUMMARY_ICON_ID) return false
        val declaredPackage = resPackage?.takeIf { it.isNotBlank() }
        return declaredPackage == null || declaredPackage == FRAMEWORK_RES_PACKAGE
    }

    /**
     * True only for the synthetic Android/HyperOS summary, not ordinary app group summaries.
     * The synthetic summary copies the representative child's smallIcon (often the app's
     * colorful launcher resource on HyperOS) and does not inherit the child's target_package
     * extras, so the flags are the only reliable marker.
     */
    fun isSyntheticAutogroupSummaryNotification(notificationFlags: Int): Boolean {
        return notificationFlags and FLAG_GROUP_SUMMARY != 0 &&
            notificationFlags and FLAG_AUTOGROUP_SUMMARY != 0
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
     * Whether a multi-color RESOURCE smallIcon should be swapped for the icon-pack glyph.
     *
     * Product rule: only when the owner's category switch allows icon-pack substitution. A
     * pack miss falls through (caller keeps the posted glyph) and SystemUI renders natively —
     * no forced silhouette, no forced tint, no white block. MiPush-managed notifications
     * always attempt (their launcher-bitmap assets would tint into blocks otherwise).
     */
    fun shouldReplaceResourceWithPackageMonochrome(
        attemptsIconPack: Boolean,
        isMiPushManaged: Boolean,
        iconType: Int,
        isGrayscaleIcon: Boolean = false,
    ): Boolean {
        if (iconType != ICON_TYPE_RESOURCE) return false
        if (isGrayscaleIcon) return false
        return attemptsIconPack
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
     * Intercept decision for the legacy getSmallIcon hook with the broken-RESOURCE guard.
     * [iconPackHit] comes from a cache-only icon-pack lookup; a miss declines so MIUI's native
     * fallback renders instead of forcing anything into the status bar.
     */
    fun shouldInterceptSmallIconWithIconGuard(
        isMiPushManaged: Boolean,
        iconPackHit: Boolean,
        iconType: Int,
        resId: Int,
        resPackage: String?,
        hasMonochromeResource: Boolean = false,
    ): Boolean {
        if (!shouldInterceptSmallIcon(isMiPushManaged, iconPackHit)) return false
        // A successful grayscale check proves that SystemUI can load this RESOURCE even when its
        // framework id is tagged with a system/server package (for example Telecom). In that case
        // keep the posted glyph and let the system tint it instead of declining to MIUI's fallback.
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

    /**
     * Pre-L tag override for the legacy setIconTag hook. Monochrome participation (managed
     * notifications, or icon-pack categories) marks the icon for single-color treatment;
     * everything else returns null so the tag passes through untouched.
     */
    fun statusBarIconPreLTagOverride(
        attemptsIconPack: Boolean,
        isMiPushManaged: Boolean,
    ): Boolean? {
        if (!shouldHandleStatusBarIcon(attemptsIconPack, isMiPushManaged)) return null
        return false
    }

    private fun shouldHandleStatusBarIcon(
        attemptsIconPack: Boolean,
        isMiPushManaged: Boolean,
    ): Boolean = attemptsIconPack || isMiPushManaged

    /**
     * Whether the notification's owner falls into a category whose icon-pack substitution is
     * enabled. MiPush-managed notifications always participate because their status-bar assets
     * are module-authored. Substitution itself still requires an icon-pack hit: a miss keeps
     * the posted glyph under SystemUI's native rendering — the verified white-block-free
     * baseline (native MIUI never tints an opaque asset).
     */
    fun shouldAttemptIconPackSubstitution(
        thirdPartyIconPack: Boolean,
        systemAppIconPack: Boolean,
        isMiPushManaged: Boolean,
        uid: Int,
        isSystemApp: Boolean,
    ): Boolean {
        if (isMiPushManaged) return true
        if (!isApplicationUid(uid) || isSystemApp) return systemAppIconPack
        return thirdPartyIconPack
    }

    /**
     * Whether MIUI must NOT substitute the multi-color launcher icon for status-bar rendering.
     * Blocking only matters where the module owns the status-bar glyph: MiPush-managed
     * notifications, icon-pack hits, and synthetic auto-group summaries under monochrome.
     * A synthetic summary loses the child's target_package marker but keeps its colorful
     * launcher icon, so the native substitution would colorize the group glyph. Everywhere
     * else, and in color mode, the native substitution IS the product behavior.
     */
    fun shouldBlockSmallIconSubstitution(
        isMiPushManaged: Boolean,
        iconPackHit: Boolean,
        isSyntheticAutogroupSummary: Boolean = false,
        colorStatusBarIcon: Boolean = false,
    ): Boolean = isMiPushManaged || iconPackHit ||
        (isSyntheticAutogroupSummary && !colorStatusBarIcon)

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

    private fun isApplicationUid(uid: Int): Boolean {
        if (uid < 0) return false
        return uid % PER_USER_RANGE >= FIRST_APPLICATION_UID
    }
}
