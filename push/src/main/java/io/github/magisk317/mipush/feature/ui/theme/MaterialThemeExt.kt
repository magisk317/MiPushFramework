package io.github.magisk317.mipush.feature.ui.theme

import androidx.compose.material3.MaterialTheme

val MaterialTheme.spacing: Spacing
    @androidx.compose.runtime.Composable
    @androidx.compose.runtime.ReadOnlyComposable
    get() = io.github.magisk317.uikit.theme.LocalSpacing.current
