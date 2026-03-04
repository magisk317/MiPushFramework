package top.trumeet.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * MaterialTheme 的扩展属性，使其可以直接访问间距常量
 * 使用方法：MaterialTheme.spacing.medium
 */
val MaterialTheme.spacing: Spacing
    @Composable
    @ReadOnlyComposable
    get() = LocalSpacing.current
