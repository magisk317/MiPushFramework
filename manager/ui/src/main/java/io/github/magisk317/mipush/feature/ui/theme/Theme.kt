package io.github.magisk317.mipush.feature.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import io.github.magisk317.uikit.theme.MagiskUiKitTheme
import io.github.magisk317.uikit.theme.UiKitColorSpec
import io.github.magisk317.uikit.theme.UiKitLayoutScale
import io.github.magisk317.uikit.theme.UiKitPaletteStyle
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

enum class ThemeAccent(val value: Int, val colorArgb: Int?) {
    System(0, null),
    Blue(1, 0xFF3F51B5.toInt()),
    Purple(2, 0xFF6750A4.toInt()),
    Green(3, 0xFF2E7D32.toInt()),
    Orange(4, 0xFFE65100.toInt()),
    Rose(5, 0xFFB3261E.toInt()),
    ;

    companion object {
        fun fromValue(value: Int): ThemeAccent =
            entries.firstOrNull { it.value == value } ?: System

        fun fromArgb(colorArgb: Int): ThemeAccent =
            entries.firstOrNull { it.colorArgb == colorArgb } ?: System
    }
}

/**
 * Single application theme root (KernelSU `App`/`Theme` model): every field
 * falls back to the stored preference unless a caller passes an explicit
 * value (MainActivity overrides all of them to drive the reveal animation).
 */
@Composable
fun Theme(
    themeMode: ThemeMode? = null,
    uiKitStyle: Int? = null,
    dynamicColor: Boolean? = null,
    accentColorArgb: Int? = null,
    monetEnabled: Boolean? = null,
    paletteStyle: Int? = null,
    colorSpec: Int? = null,
    surfaceBlur: Boolean? = null,
    layoutScale: Int? = null,
    content: @Composable () -> Unit,
) {
    val preferenceRepository: io.github.magisk317.mipush.data.PreferenceRepository =
        org.koin.compose.koinInject()
    val storedThemeMode by preferenceRepository.themeMode.collectAsState(initial = ThemeMode.System.value)
    val storedUiKitStyle by preferenceRepository.uiKitStyle.collectAsState(initial = UiKitStyle.Expressive.value)
    val storedDynamicColor by preferenceRepository.themeDynamicColor.collectAsState(initial = true)
    val storedAccentColor by preferenceRepository.themeAccentColor.collectAsState(initial = 0)
    val storedMonetEnabled by preferenceRepository.themeMonetEnabled.collectAsState(initial = false)
    val storedPaletteStyle by preferenceRepository.themePaletteStyle.collectAsState(initial = UiKitPaletteStyle.TonalSpot.value)
    val storedColorSpec by preferenceRepository.themeColorSpec.collectAsState(initial = UiKitColorSpec.Spec2025.value)
    val storedSurfaceBlur by preferenceRepository.themeSurfaceBlur.collectAsState(initial = false)
    val storedLayoutScale by preferenceRepository.uiLayoutScale.collectAsState(initial = UiKitLayoutScale.Standard.value)

    MagiskUiKitTheme(
        themeMode = (themeMode ?: ThemeMode.fromValue(storedThemeMode)).value,
        uiKitStyle = UiKitStyle.fromValue(uiKitStyle ?: storedUiKitStyle),
        dynamicColor = dynamicColor ?: storedDynamicColor,
        accentColor = accentColorArgb ?: storedAccentColor,
        monetEnabled = monetEnabled ?: storedMonetEnabled,
        paletteStyle = UiKitPaletteStyle.fromValue(paletteStyle ?: storedPaletteStyle),
        colorSpec = UiKitColorSpec.fromValue(colorSpec ?: storedColorSpec),
        surfaceBlur = surfaceBlur ?: storedSurfaceBlur,
        layoutScale = UiKitLayoutScale.fromValue(layoutScale ?: storedLayoutScale),
        content = content,
    )
}
