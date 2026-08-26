package io.github.magisk317.mipush.common.island

data class IslandOptions(
    val enabled: Boolean = true,
    val timeoutSecs: Int = 5,
    val firstFloat: Boolean = true,
    val enableFloat: Boolean = true,
    val showNotification: Boolean = true,
    val showOriginalNotification: Boolean = true,
    val focusNotification: Boolean = false,
    val rendererMode: IslandRendererMode = IslandRendererMode.AUTO,
    val visualEnabled: Boolean = true,
    val dynamicColor: Boolean = true,
    val blurEnabled: Boolean = true,
    val glassEnabled: Boolean = true,
    val outerGlowEnabled: Boolean = true,
    val animationEnabled: Boolean = true,
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
        ISLAND_PREF_RENDERER_MODE to rendererMode.wireValue,
        ISLAND_PREF_VISUAL_ENABLED to visualEnabled.toFlagValue(),
        ISLAND_PREF_DYNAMIC_COLOR to dynamicColor.toFlagValue(),
        ISLAND_PREF_BLUR_ENABLED to blurEnabled.toFlagValue(),
        ISLAND_PREF_GLASS_ENABLED to glassEnabled.toFlagValue(),
        ISLAND_PREF_OUTER_GLOW_ENABLED to outerGlowEnabled.toFlagValue(),
        ISLAND_PREF_ANIMATION_ENABLED to animationEnabled.toFlagValue(),
        COLOR_STATUS_BAR_ICON_KEY to colorStatusBarIcon.toFlagValue(),
        COLOR_STATUS_BAR_ICON_GLOBAL_KEY to colorStatusBarIconGlobal.toFlagValue(),
        DUAL_APP_ENABLED_KEY to dualAppEnabled.toFlagValue(),
        LOG_SANITIZATION_ENABLED_KEY to logSanitizationEnabled.toFlagValue(),
    )

    private fun Boolean.toFlagValue(): String = if (this) "1" else "0"
}
