@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.ScrollState
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.DoubleTapToTopOverlay
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Expressive/Material chrome for the Settings tab (collapsing bar template). */
@Composable
internal fun SettingsExpressive(
    contentPadding: PaddingValues,
    scrollChromeState: ScrollChromeState?,
    scrollState: ScrollState,
    scrollScope: CoroutineScope,
    snackbarHostState: AppSnackbarHostState,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                AppTopBar(
                    modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                    containerColor = if (glassOn) Color.Transparent else MaterialTheme.colorScheme.surface,
                    title = stringResource(R.string.main_settings),
                    scrollBehavior = scrollBehavior,
                )
                DoubleTapToTopOverlay(
                    onDoubleTap = { scrollScope.launch { scrollState.animateScrollTo(0) } },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 72.dp, top = statusBarTop, end = 112.dp)
                        .fillMaxWidth()
                        .height(64.dp),
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()
                    .then(if (glassOn) topGlass.contentRecorder() else Modifier)) {
            body(
                PaddingValues(top = innerPadding.calculateTopPadding()),
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            )
            AppSnackbarHost(
                hostState = snackbarHostState,
                bottomPadding = contentPadding.calculateBottomPadding() + 16.dp,
            )
            // No add-FAB on this page: keep the default scroll-to-top FAB offset.
            ScrollToTopFAB(
                scrollState,
                visible = scrollChromeState?.isChromeVisible != true,
                extraBottomPadding = contentPadding.calculateBottomPadding(),
            )
        }
    }
}
