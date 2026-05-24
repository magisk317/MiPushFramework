package io.github.magisk317.mipush.feature.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.xiaomi.xmsf.R
import io.github.magisk317.uikit.R as UiKitR
import kotlinx.coroutines.launch

@Composable
fun BoxScope.ScrollToTopFAB(listState: LazyListState) {
    val isAtTop by remember {
        derivedStateOf { listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0 }
    }
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = !isAtTop,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        FloatingActionButton(
            onClick = { scope.launch { listState.animateScrollToItem(0) } },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                painter = painterResource(UiKitR.drawable.ic_keyboard_arrow_up_black_24dp),
                contentDescription = stringResource(R.string.action_scroll_to_top),
            )
        }
    }
}

@Composable
fun BoxScope.ScrollToTopFAB(scrollState: ScrollState) {
    val isAtTop by remember { derivedStateOf { scrollState.value == 0 } }
    val scope = rememberCoroutineScope()
    AnimatedVisibility(
        visible = !isAtTop,
        modifier = Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = 16.dp),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
    ) {
        FloatingActionButton(
            onClick = { scope.launch { scrollState.animateScrollTo(0) } },
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ) {
            Icon(
                painter = painterResource(UiKitR.drawable.ic_keyboard_arrow_up_black_24dp),
                contentDescription = stringResource(R.string.action_scroll_to_top),
            )
        }
    }
}
