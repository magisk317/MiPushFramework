package top.trumeet.mipushframework.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
fun AppLinearLoadingIndicator(modifier: Modifier = Modifier) {
    LinearProgressIndicator(
        modifier = modifier.fillMaxWidth(),
    )
}
