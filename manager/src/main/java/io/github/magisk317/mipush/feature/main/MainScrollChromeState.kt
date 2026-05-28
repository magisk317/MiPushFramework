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
import kotlin.math.abs

private const val CHROME_SCROLL_THRESHOLD_PX = 32
private const val BOTTOM_REVEAL_SCROLL_THRESHOLD_PX = 96

class MainScrollChromeState {
    var isChromeVisible by mutableStateOf(true)
        private set

    private var accumulatedScrollDelta = 0
    private var wasAtBottom = false
    private var accumulatedBottomRevealDelta = 0

    fun show() {
        isChromeVisible = true
        accumulatedScrollDelta = 0
        wasAtBottom = false
        accumulatedBottomRevealDelta = 0
    }

    fun hide() {
        isChromeVisible = false
        accumulatedScrollDelta = 0
        wasAtBottom = false
        accumulatedBottomRevealDelta = 0
    }

    fun onScrollDelta(delta: Int, atTop: Boolean = false, atBottom: Boolean = false) {
        if (delta == 0) return
        when {
            atTop -> show()
            atBottom -> {
                wasAtBottom = true
                accumulatedBottomRevealDelta = 0
                if (delta < 0) accumulatedScrollDelta = 0 else handleDirectionalDelta(delta)
            }
            wasAtBottom && delta < 0 -> handleBottomExitDelta(delta)
            else -> {
                if (delta > 0) wasAtBottom = false
                accumulatedBottomRevealDelta = 0
                handleDirectionalDelta(delta)
            }
        }
    }

    private fun handleBottomExitDelta(delta: Int) {
        accumulatedBottomRevealDelta = if (accumulatedBottomRevealDelta.signMatches(delta)) {
            accumulatedBottomRevealDelta + delta
        } else {
            delta
        }
        if (abs(accumulatedBottomRevealDelta) < BOTTOM_REVEAL_SCROLL_THRESHOLD_PX) return
        show()
    }

    private fun handleDirectionalDelta(delta: Int, thresholdPx: Int = CHROME_SCROLL_THRESHOLD_PX) {
        accumulatedScrollDelta = if (accumulatedScrollDelta.signMatches(delta)) {
            accumulatedScrollDelta + delta
        } else {
            delta
        }
        if (abs(accumulatedScrollDelta) < thresholdPx) return
        if (accumulatedScrollDelta > 0) hide() else show()
    }
}

private fun Int.signMatches(other: Int): Boolean {
    return this == 0 || (this > 0 && other > 0) || (this < 0 && other < 0)
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
                    index > previousIndex -> CHROME_SCROLL_THRESHOLD_PX
                    index < previousIndex -> -CHROME_SCROLL_THRESHOLD_PX
                    else -> offset - previousOffset
                }
                val layoutInfo = state.layoutInfo
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
                val atBottom = lastVisible != null &&
                    lastVisible.index >= layoutInfo.totalItemsCount - 1 &&
                    lastVisible.offset + lastVisible.size <= layoutInfo.viewportEndOffset
                target.onScrollDelta(delta, atTop = index == 0 && offset == 0, atBottom = atBottom)
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
                target.onScrollDelta(value - previousValue, atTop = value == 0, atBottom = value >= state.maxValue)
                previousValue = value
            }
    }
}
