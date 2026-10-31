package io.github.magisk317.mipush.common.island

data class IslandOptions(
    val enabled: Boolean = true,
    val timeoutSecs: Int = 5,
    val firstFloat: Boolean = true,
    val enableFloat: Boolean = true,
    val showNotification: Boolean = true,
    val showOriginalNotification: Boolean = true,
    val focusNotification: Boolean = false,
    val colorStatusBarIcon: Boolean = false,
    val monochromeShadeIcon: Boolean = false,
    val thirdPartyIconPack: Boolean = true,
    val systemAppIconPack: Boolean = true,
    val dualAppEnabled: Boolean = false,
    /**
     * Package-scoped only: keep HyperOS CountLimitCoordinator from dismissing this package's
     * notifications beyond its per-package cap. Never set by a global snapshot read.
     */
    val notificationCountLimitExempt: Boolean = false,
) {
    val canInjectFocusPayload: Boolean
        get() = enabled && focusNotification && enableFloat

    val canBuildFocusPayload: Boolean
        get() = canInjectFocusPayload

    fun toPreferenceFlags(): Map<String, String> = mapOf(
        ISLAND_PREF_ENABLED to enabled.toFlagValue(),
        ISLAND_PREF_TIMEOUT to timeoutSecs.coerceAtLeast(1).toString(),
        ISLAND_PREF_FIRST_FLOAT to firstFloat.toFlagValue(),
        ISLAND_PREF_ENABLE_FLOAT to enableFloat.toFlagValue(),
        ISLAND_PREF_SHOW_NOTIFICATION to showNotification.toFlagValue(),
        ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION to showOriginalNotification.toFlagValue(),
        ISLAND_PREF_FOCUS_NOTIF to focusNotification.toFlagValue(),
        COLOR_STATUS_BAR_ICON_KEY to colorStatusBarIcon.toFlagValue(),
        MONOCHROME_SHADE_ICON_KEY to monochromeShadeIcon.toFlagValue(),
        THIRD_PARTY_ICON_PACK_KEY to thirdPartyIconPack.toFlagValue(),
        SYSTEM_APP_ICON_PACK_KEY to systemAppIconPack.toFlagValue(),
        DUAL_APP_ENABLED_KEY to dualAppEnabled.toFlagValue(),
        NOTIFICATION_COUNT_LIMIT_EXEMPT_KEY to notificationCountLimitExempt.toFlagValue(),
    )

    private fun Boolean.toFlagValue(): String = if (this) "1" else "0"
}
