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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
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
import io.github.magisk317.uikit.surface.ScrollToTopFAB

/** Expressive/Material chrome for the Settings tab (collapsing bar template). */
@Composable
internal fun SettingsExpressive(
    contentPadding: PaddingValues,
    scrollChromeState: ScrollChromeState?,
    scrollState: ScrollState,
    snackbarHostState: AppSnackbarHostState,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                containerColor = if (glassOn) Color.Transparent else MaterialTheme.colorScheme.surface,
                title = stringResource(R.string.main_settings),
                scrollBehavior = scrollBehavior,
            )
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
            ScrollToTopFAB(
                scrollState,
                visible = scrollChromeState?.isChromeVisible != true,
                extraBottomPadding = contentPadding.calculateBottomPadding(),
            )
        }
    }
}
