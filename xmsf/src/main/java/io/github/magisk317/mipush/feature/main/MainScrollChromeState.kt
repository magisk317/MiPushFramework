package io.github.magisk317.mipush.feature.main

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class MainScrollChromeState {
    var isChromeVisible by mutableStateOf(true)
        private set

    fun show() {
        isChromeVisible = true
    }

    fun hide() {
        isChromeVisible = false
    }

    fun onScrollDelta(delta: Int, atTop: Boolean = false) {
        when {
            atTop -> show()
            delta > 0 -> hide()
            delta < 0 -> show()
        }
    }
}

@Composable
fun rememberMainScrollChromeState(): MainScrollChromeState = remember { MainScrollChromeState() }

@Composable
fun ReportLazyListScrollToChrome(
    state: LazyListState,
    chromeState: MainScrollChromeState?,
) {
    androidx.compose.runtime.LaunchedEffect(state, chromeState) {
        val target = chromeState ?: return@LaunchedEffect
        var previousIndex = state.firstVisibleItemIndex
        var previousOffset = state.firstVisibleItemScrollOffset
        snapshotFlow { state.firstVisibleItemIndex to state.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { (index, offset) ->
                val delta = when {
                    index > previousIndex -> 1
                    index < previousIndex -> -1
                    else -> offset.compareTo(previousOffset)
                }
                target.onScrollDelta(delta, atTop = index == 0 && offset == 0)
                previousIndex = index
                previousOffset = offset
            }
    }
}

@Composable
fun ReportScrollStateToChrome(
    state: ScrollState,
    chromeState: MainScrollChromeState?,
) {
    androidx.compose.runtime.LaunchedEffect(state, chromeState) {
        val target = chromeState ?: return@LaunchedEffect
        var previousValue = state.value
        snapshotFlow { state.value }
            .distinctUntilChanged()
            .collect { value ->
                target.onScrollDelta(value.compareTo(previousValue), atTop = value == 0)
                previousValue = value
            }
    }
}
