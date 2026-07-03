package io.github.magisk317.mipush.hook.systemui

import android.os.Bundle
import io.github.magisk317.mipush.hook.island.IslandDispatchContract

internal object SystemUiNotificationPolicy {
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
        isMiPushManaged: Boolean,
    ): Boolean = colorStatusBarIcon && isMiPushManaged

    fun shouldApplySmallIconColor(
        colorStatusBarIcon: Boolean,
        isMiPushManaged: Boolean,
        isGrayscaleIcon: Boolean,
    ): Boolean = colorStatusBarIcon && isMiPushManaged && !isGrayscaleIcon

    fun shouldForcePreLIconTag(
        colorStatusBarIcon: Boolean,
        isMiPushManaged: Boolean,
    ): Boolean = colorStatusBarIcon && isMiPushManaged
}
