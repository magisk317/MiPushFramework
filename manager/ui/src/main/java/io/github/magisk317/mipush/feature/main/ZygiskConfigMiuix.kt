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
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.surface.DoubleTapToTopOverlay
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.surfaceBlurContainerColor
import io.github.magisk317.uikit.surface.uiKitSurfaceBlur
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Miuix chrome for the Zygisk config activity (collapsing bar template). */
@Composable
internal fun ZygiskConfigMiuix(
    title: String,
    listState: LazyListState,
    scrollScope: CoroutineScope,
    snackbarHostState: AppSnackbarHostState,
    floatingActionButton: @Composable () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                TopAppBar(
                    modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                    title = title,
                    scrollBehavior = scrollBehavior,
                    color = if (glassOn) Color.Transparent else MiuixTheme.colorScheme.surface,
                    defaultWindowInsetsPadding = true,
                )
                DoubleTapToTopOverlay(
                    onDoubleTap = { scrollScope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 72.dp, top = topInset, end = 112.dp)
                        .fillMaxWidth()
                        .height(64.dp),
                )
            }
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().then(if (glassOn) topGlass.contentRecorder() else Modifier).nestedScroll(scrollBehavior.nestedScrollConnection)) {
            body(innerPadding, Modifier.scrollEndHaptic())
        }
    }
}
