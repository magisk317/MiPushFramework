package top.trumeet.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import com.magisk317.data.DataStoreManager
import io.github.magisk317.uikit.theme.MagiskThemeMode
import io.github.magisk317.uikit.theme.MagiskUiKitTheme

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
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val storedThemeMode = DataStoreManager.themeMode
        .collectAsState(initial = ThemeMode.System.value)
        .value
    val resolvedMode = themeMode ?: ThemeMode.fromValue(storedThemeMode)

    MagiskUiKitTheme(
        themeMode = resolvedMode.value,
        dynamicColor = dynamicColor,
        content = content,
    )
}
