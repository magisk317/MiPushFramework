package io.github.magisk317.mipush.common.island

import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_GLOBAL_KEY
import io.github.magisk317.mipush.common.DUAL_APP_ENABLED_KEY
import io.github.magisk317.mipush.common.COLOR_STATUS_BAR_ICON_KEY
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLE_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_FIRST_FLOAT
import io.github.magisk317.mipush.common.ISLAND_PREF_FOCUS_NOTIF
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION
import io.github.magisk317.mipush.common.ISLAND_PREF_TIMEOUT
import io.github.magisk317.mipush.common.LOG_SANITIZATION_ENABLED_KEY

data class IslandOptions(
    val enabled: Boolean = true,
    val timeoutSecs: Int = 5,
    val firstFloat: Boolean = true,
    val enableFloat: Boolean = true,
    val showNotification: Boolean = true,
    val showOriginalNotification: Boolean = true,
    val focusNotification: Boolean = false,
    val colorStatusBarIcon: Boolean = false,
    val colorStatusBarIconGlobal: Boolean = false,
    val dualAppEnabled: Boolean = false,
) {
    val canInjectFocusPayload: Boolean
        get() = enabled && focusNotification && enableFloat

    val canBuildFocusPayload: Boolean
        get() = canInjectFocusPayload

    fun toPreferenceFlags(logSanitizationEnabled: Boolean = false): Map<String, String> = mapOf(
        ISLAND_PREF_ENABLED to enabled.toFlagValue(),
        ISLAND_PREF_TIMEOUT to timeoutSecs.coerceAtLeast(1).toString(),
        ISLAND_PREF_FIRST_FLOAT to firstFloat.toFlagValue(),
        ISLAND_PREF_ENABLE_FLOAT to enableFloat.toFlagValue(),
        ISLAND_PREF_SHOW_NOTIFICATION to showNotification.toFlagValue(),
        ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION to showOriginalNotification.toFlagValue(),
        ISLAND_PREF_FOCUS_NOTIF to focusNotification.toFlagValue(),
        COLOR_STATUS_BAR_ICON_KEY to colorStatusBarIcon.toFlagValue(),
        COLOR_STATUS_BAR_ICON_GLOBAL_KEY to colorStatusBarIconGlobal.toFlagValue(),
        DUAL_APP_ENABLED_KEY to dualAppEnabled.toFlagValue(),
        LOG_SANITIZATION_ENABLED_KEY to logSanitizationEnabled.toFlagValue(),
    )

    private fun Boolean.toFlagValue(): String = if (this) "1" else "0"
}
