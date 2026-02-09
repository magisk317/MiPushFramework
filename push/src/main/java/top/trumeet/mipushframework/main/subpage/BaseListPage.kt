package top.trumeet.mipushframework.main.subpage

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import top.trumeet.ui.theme.Theme

@Composable
fun Page(content: @Composable () -> Unit) {
    val context = LocalContext.current

    Theme {
        Surface(color = androidx.compose.material3.MaterialTheme.colorScheme.background, content = content)
    }
}
