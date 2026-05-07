package io.github.magisk317.mipush.feature.ui.component

import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.material3.MaterialTheme

@Composable
fun TextView(text: CharSequence, modifier: Modifier = Modifier, textSize: Float? = null) {
    val textColor = MaterialTheme.colorScheme.onSurface.toArgb()
    val linkColor = MaterialTheme.colorScheme.primary.toArgb()
    AndroidView(modifier = modifier, factory = { context ->
        TextView(context).apply {
            minHeight = 0
            setTextIsSelectable(true) // must before movementMethod
            isFocusable = true
            movementMethod = LinkMovementMethod.getInstance()
        }
    }, update = { it ->
        it.apply {
            this.text = text
            setTextColor(textColor)
            setLinkTextColor(linkColor)
            textSize?.let { setTextSize(it) }
        }
    })
}

@Preview(showBackground = true)
@Composable
private fun Preview() {
    TextView("# Hello, Compose!\n\nThis is **Markdown** rendering in Compose!")
}
