package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import io.github.magisk317.mipush.feature.ui.theme.Theme

@Composable
fun Page(content: @Composable () -> Unit) {
    val context = LocalContext.current

    Theme {
        Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background, content = content)
    }
}
