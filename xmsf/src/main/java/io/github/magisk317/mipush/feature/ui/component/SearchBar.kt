package io.github.magisk317.mipush.feature.ui.component

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import io.github.magisk317.uikit.surface.WorkspaceSearchField

@Composable
fun SearchBar(
    placeholder: String,
    query: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    val ph by rememberUpdatedState(placeholder)
    WorkspaceSearchField(
        query = query,
        placeholder = ph,
        modifier = modifier,
        onValueChange = onValueChange,
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
