package io.github.magisk317.mipush.feature.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import com.magisk317.data.PreferenceRepository
import io.github.magisk317.uikit.theme.MagiskUiKitTheme
import io.github.magisk317.uikit.theme.UiKitStyle

enum class ThemeMode(val value: Int) {
    System(0),
    Light(1),
    Dark(2),
    PureBlack(3),
    ;

    companion object {
        fun fromValue(value: Int): ThemeMode {
            return entries.firstOrNull { it.value == value } ?: System
        }
    }
}

@Composable
fun Theme(
    themeMode: ThemeMode? = null,
    uiKitStyle: Int? = null,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val preferenceRepository = remember { PreferenceRepository() }
    val storedThemeMode = preferenceRepository.themeMode
        .collectAsState(initial = ThemeMode.System.value)
        .value
    val storedUiKitStyle = preferenceRepository.uiKitStyle
        .collectAsState(initial = UiKitStyle.Expressive.value)
        .value
    val resolvedMode = themeMode ?: ThemeMode.fromValue(storedThemeMode)
    val resolvedUiKitStyle = uiKitStyle ?: storedUiKitStyle

    MagiskUiKitTheme(
        themeMode = resolvedMode.value,
        uiKitStyle = UiKitStyle.fromValue(resolvedUiKitStyle),
        dynamicColor = dynamicColor,
        content = content,
    )
}
