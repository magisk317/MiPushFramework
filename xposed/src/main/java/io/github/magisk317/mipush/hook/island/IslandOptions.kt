package io.github.magisk317.mipush.hook.island

data class IslandOptions(
    val enabled: Boolean = true,
    val timeoutSecs: Int = 5,
    val firstFloat: Boolean = true,
    val enableFloat: Boolean = true,
    val showNotification: Boolean = true,
    val focusNotification: Boolean = true,
) {
    val canInjectFocusPayload: Boolean
        get() = enabled && focusNotification
}
