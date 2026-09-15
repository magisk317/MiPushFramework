@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main

import androidx.compose.foundation.layout.PaddingValues
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.uiKitRecordSurfaceGlassBackdrop
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.compose.material3.Scaffold
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.surface.AppTopBar

/** Expressive/Material chrome for the Zygisk config activity (collapsing bar). */
@Composable
internal fun ZygiskConfigExpressive(
    title: String,
    snackbarHostState: AppSnackbarHostState,
    floatingActionButton: @Composable () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val topGlass = if (LocalUiKitSurfaceBlur.current.usesBackdrop) rememberUiKitGlassTopBar() else null
    Scaffold(
        topBar = {
            AppTopBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                containerColor = if (topGlass != null) Color.Transparent else MaterialTheme.colorScheme.surface,
                title = title,
                scrollBehavior = scrollBehavior,
            )
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
        }
    }
}
