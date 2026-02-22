@file:OptIn(ExperimentalMaterial3Api::class)
package top.trumeet.mipushframework.component

import android.os.SystemClock
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults.Indicator
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

@Composable
fun RefreshableLazyColumn(
    doRefresh: (onRefreshed: () -> Unit) -> Unit,
    isNeedMore: (lastVisibleIndex: Int) -> Boolean,
    doLoadMore: (onRefreshed: () -> Unit) -> Unit,
    isNeedRefresh: Boolean = false,
    scrollToTopSignal: Int = 0,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    content: LazyListScope.() -> Unit
) {
    val currentIsNeedMore by rememberUpdatedState(isNeedMore)
    val currentDoLoadMore by rememberUpdatedState(doLoadMore)

    var isRefreshing by remember { mutableStateOf(false) }
    var refreshStartedAt by remember { mutableStateOf(0L) }
    val onRefreshed = remember { { isRefreshing = false } }
    val scope = rememberCoroutineScope()

    val onRefreshedWithMinDuration = remember {
        {
            val elapsed = if (refreshStartedAt > 0L) {
                SystemClock.elapsedRealtime() - refreshStartedAt
            } else {
                LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS
            }
            val remaining = (LoadingIndicatorTokens.MIN_VISIBLE_DURATION_MILLIS - elapsed).coerceAtLeast(0L)
            scope.launch {
                if (remaining > 0L) delay(remaining)
                onRefreshed()
            }
            Unit
        }
    }

    if (isNeedRefresh) {
        isRefreshing = true
        refreshStartedAt = SystemClock.elapsedRealtime()
        SideEffect {
            doRefresh(onRefreshedWithMinDuration)
        }
    }

    val state = rememberPullToRefreshState()

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            isRefreshing = true
            refreshStartedAt = SystemClock.elapsedRealtime()
            doRefresh(onRefreshedWithMinDuration)
        },
        state = state,
        modifier = Modifier.fillMaxSize(),
        indicator = {
            Indicator(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = contentPadding.calculateTopPadding() + LoadingIndicatorTokens.OverlayTopSpacing),
                isRefreshing = isRefreshing,
                state = state
            )
        }
    ) {
        val lazyListState = rememberLazyListState()
        LaunchedEffect(scrollToTopSignal) {
            if (scrollToTopSignal > 0) {
                lazyListState.scrollToItem(0)
            }
        }
        LaunchedEffect(lazyListState) {
            snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
                .distinctUntilChanged()
                .collect { lastIndex ->
                    if (isRefreshing) return@collect
                    if (currentIsNeedMore(lastIndex)) {
                        isRefreshing = true
                        refreshStartedAt = SystemClock.elapsedRealtime()
                        currentDoLoadMore(onRefreshedWithMinDuration)
                    }
                }
        }
        LazyColumn(
            Modifier.fillMaxSize(),
            state = lazyListState,
            contentPadding = contentPadding,
            content = content
        )
    }
}
