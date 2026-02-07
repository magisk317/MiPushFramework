package top.trumeet.mipushframework.component

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.input.ImeAction
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun SearchBar(
    placeholder: String,
    query: String,
    onValueChange: (String) -> Unit
) {
    val focusManager = LocalFocusManager.current
    val ph by rememberUpdatedState(placeholder)
    val debounceOnValueChange: (String) -> Unit = debounce(onValueChange)
    val change: (String) -> Unit = { debounceOnValueChange(it) }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        tonalElevation = 0.dp,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        TextField(
            value = query,
            onValueChange = change,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp),
            placeholder = { Text(ph) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton({ change("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
            colors = TextFieldDefaults.colors(
                unfocusedContainerColor = Color.Transparent,
                focusedContainerColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent
            )
        )
    }
}

@Composable
fun SearchBar(placeholder: String, onValueChange: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    SearchBar(
        placeholder = placeholder,
        query = query,
        onValueChange = {
            query = it
            onValueChange(it)
        }
    )
}

@Composable
private fun debounce(onValueChange: (String) -> Unit): (String) -> Unit {
    val scope = rememberCoroutineScope()
    var job: Job? = null
    val change: (String) -> Unit = {
        scope.launch {
            job?.cancel()
            job = launch {
                delay(300)
                onValueChange(it)
            }
        }
    }
    return change
}
