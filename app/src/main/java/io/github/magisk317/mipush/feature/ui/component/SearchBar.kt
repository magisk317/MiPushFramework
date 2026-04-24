package io.github.magisk317.mipush.feature.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.magisk317.uikit.surface.WorkspaceSearchField
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SearchBar(
    placeholder: String,
    query: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    val ph by rememberUpdatedState(placeholder)
    val debounceOnValueChange: (String) -> Unit = debounce(onValueChange)
    val change: (String) -> Unit = { debounceOnValueChange(it) }
    WorkspaceSearchField(
        query = query,
        placeholder = ph,
        modifier = modifier,
        onValueChange = change,
    )
}

@Composable
fun SearchBar(
    placeholder: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    var query by rememberSaveable { mutableStateOf("") }
    SearchBar(
        placeholder = placeholder,
        query = query,
        modifier = modifier,
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
