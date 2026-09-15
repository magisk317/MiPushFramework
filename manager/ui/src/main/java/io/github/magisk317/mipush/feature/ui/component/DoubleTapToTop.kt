package io.github.magisk317.mipush.feature.ui.component

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextOverflow

@Composable
internal fun DoubleTapToTopTitle(
    text: String,
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
    maxLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Ellipsis,
) {
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { currentOnDoubleTap() },
            )
        },
        maxLines = maxLines,
        overflow = overflow,
    )
}

@Composable
internal fun DoubleTapToTopOverlay(
    onDoubleTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentOnDoubleTap by rememberUpdatedState(onDoubleTap)
    androidx.compose.foundation.layout.Box(
        modifier = modifier.pointerInput(Unit) {
            detectTapGestures(
                onDoubleTap = { currentOnDoubleTap() },
            )
        },
    )
}
