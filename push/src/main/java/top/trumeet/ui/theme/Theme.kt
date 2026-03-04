package top.trumeet.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.MaterialExpressiveTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalContext
import com.magisk317.data.DataStoreManager
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
//    primary = Purple80,
//    secondary = PurpleGrey80,
//    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
//    primary = Purple40,
//    secondary = PurpleGrey40,
//    tertiary = Pink40

        /* Other default colors to override
        background = Color(0xFFFFFBFE),
        surface = Color(0xFFFFFBFE),
        onPrimary = Color.White,
        onSecondary = Color.White,
        onTertiary = Color.White,
        onBackground = Color(0xFF1C1B1F),
        onSurface = Color(0xFF1C1B1F),
        */
)

enum class ThemeMode(val value: Int) {
    System(0),
    Light(1),
    Dark(2),
    PureBlack(3);

    companion object {
        fun fromValue(value: Int): ThemeMode {
            return entries.firstOrNull { it.value == value } ?: System
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun Theme(
    themeMode: ThemeMode? = null,
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val storedThemeMode = DataStoreManager.themeMode.collectAsState(initial = ThemeMode.System.value).value
    val resolvedMode = themeMode ?: ThemeMode.fromValue(storedThemeMode)
    val darkTheme = when (resolvedMode) {
        ThemeMode.Light -> false
        ThemeMode.Dark, ThemeMode.PureBlack -> true
        ThemeMode.System -> isSystemInDarkTheme()
    }

    val colorScheme = when {
        resolvedMode == ThemeMode.PureBlack -> {
            val context = LocalContext.current
            val darkBase = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                dynamicDarkColorScheme(context)
            } else {
                DarkColorScheme
            }
            darkBase.copy(
                background = Color.Black,
                surface = Color.Black,
                surfaceContainer = Color.Black,
                surfaceContainerLow = Color.Black,
                surfaceContainerLowest = Color.Black,
                surfaceContainerHigh = Color.Black,
                surfaceContainerHighest = Color.Black,
            )
        }

        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    UpdateSystemBars(darkTheme)
    
    CompositionLocalProvider(
        LocalSpacing provides Spacing()
    ) {
        MaterialExpressiveTheme(
            colorScheme = colorScheme,
            shapes = Shapes,
            content = content
        )
    }
}
