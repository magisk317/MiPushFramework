package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.surface.AppSurface
import androidx.compose.runtime.Composable

@Composable
fun Page(content: @Composable () -> Unit) {
    AppSurface(color = MaterialTheme.colorScheme.background, content = content)
}
