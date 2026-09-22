@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyListState
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.uiKitRecordSurfaceGlassBackdrop
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import androidx.compose.ui.graphics.Color
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.DoubleTapToTopOverlay
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Expressive/Material chrome for the Zygisk config activity (collapsing bar). */
@Composable
internal fun ZygiskConfigExpressive(
    title: String,
    listState: LazyListState,
    scrollScope: CoroutineScope,
    snackbarHostState: AppSnackbarHostState,
    floatingActionButton: @Composable () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topGlass = if (LocalUiKitSurfaceBlur.current.usesBackdrop) rememberUiKitGlassTopBar() else null
    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                AppTopBar(
                    modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                    containerColor = if (topGlass != null) Color.Transparent else MaterialTheme.colorScheme.surface,
                    title = title,
                    scrollBehavior = scrollBehavior,
                )
                DoubleTapToTopOverlay(
                    onDoubleTap = { scrollScope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 72.dp, top = statusBarTop, end = 112.dp)
                        .fillMaxWidth()
                        .height(64.dp),
                )
            }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .uiKitRecordSurfaceGlassBackdrop(topGlass),
        ) {
            body(innerPadding, Modifier.nestedScroll(scrollBehavior.nestedScrollConnection))
            // The page owns a bottom-end save FAB; lift the scroll-to-top FAB above it.
            ScrollToTopFAB(
                listState = listState,
                extraBottomPadding = 96.dp,
            )
        }
    }
}
