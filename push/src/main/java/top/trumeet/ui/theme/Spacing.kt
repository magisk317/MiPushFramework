package top.trumeet.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 全局统一的间距令牌 (Spacing Tokens)
 * 根据 Material 3 栅格化标准定义
 */
@Immutable
data class Spacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val superLarge: Dp = 32.dp
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
