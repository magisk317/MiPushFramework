package io.github.magisk317.mipush.feature.ui.component

import android.os.SystemClock
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.scroll.uiKitScrollEndHaptic
import io.github.magisk317.uikit.foundation.LoadingIndicatorTokens
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.AppPullToRefresh
import io.github.magisk317.uikit.scroll.ReportLazyListScrollToChrome
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun RefreshableLazyColumn(
    doRefresh: (onRefreshed: () -> Unit) -> Unit,
    isNeedMore: (lastVisibleIndex: Int) -> Boolean,
    doLoadMore: (onRefreshed: () -> Unit) -> Unit,
    modifier: Modifier = Modifier,
    isNeedRefresh: Boolean = false,
    scrollToTopSignal: Int = 0,
    scrollToTopAfterRefresh: Boolean = false,
    scrollChromeState: ScrollChromeState? = null,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    listState: LazyListState? = null,
    content: LazyListScope.() -> Unit
) {
    val currentIsNeedMore by rememberUpdatedState(isNeedMore)
    val currentDoLoadMore by rememberUpdatedState(doLoadMore)
    val currentDoRefresh by rememberUpdatedState(doRefresh)

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshStartedAt by remember { mutableLongStateOf(0L) }
    val onRefreshed = remember { { isRefreshing = false } }
    val scope = rememberCoroutineScope()
    val lazyListState = listState ?: rememberLazyListState()

    val finishRefresh = remember(lazyListState, scrollToTopAfterRefresh) {
        {
            val elapsed = if (refreshStartedAt > 0L) {
                SystemClock.elapsedRealtime() - refreshStartedAt
            } else {
                LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS
            }
            val remaining = (LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS - elapsed).coerceAtLeast(0L)
            scope.launch {
                if (remaining > 0L) delay(remaining)
                if (scrollToTopAfterRefresh && lazyListState.layoutInfo.totalItemsCount > 0) {
                    lazyListState.scrollToItem(0)
                }
                onRefreshed()
            }
            Unit
        }
    }
    val finishLoadMore = remember {
        {
            scope.launch {
                onRefreshed()
            }
            Unit
        }
    }

    LaunchedEffect(isNeedRefresh) {
        if (!isNeedRefresh) return@LaunchedEffect
        isRefreshing = true
        refreshStartedAt = SystemClock.elapsedRealtime()
        currentDoRefresh(finishRefresh)
    }

    AppPullToRefresh(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshStartedAt = SystemClock.elapsedRealtime()
            doRefresh(finishRefresh)
        },
        modifier = modifier.fillMaxSize(),
        contentPadding = contentPadding,
    ) {
        io.github.magisk317.uikit.surface.ScrollToTopEffect(lazyListState, scrollToTopSignal)
        ReportLazyListScrollToChrome(lazyListState, scrollChromeState)
        LaunchedEffect(lazyListState) {
            snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .distinctUntilChanged()
                .collect { lastIndex ->
                    if (isRefreshing) return@collect
                    if (currentIsNeedMore(lastIndex)) {
                        isRefreshing = true
                        refreshStartedAt = SystemClock.elapsedRealtime()
                        currentDoLoadMore(finishLoadMore)
                    }
                }
        }
        LazyColumn(
            Modifier
                .fillMaxSize()
                .uiKitScrollEndHaptic(),
            state = lazyListState,
            contentPadding = contentPadding,
            content = content
        )
    }
}
