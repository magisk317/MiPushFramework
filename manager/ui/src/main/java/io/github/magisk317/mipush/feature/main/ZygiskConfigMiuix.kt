package io.github.magisk317.mipush.feature.main

import androidx.compose.foundation.layout.Box
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.surfaceBlurContainerColor
import io.github.magisk317.uikit.surface.uiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Miuix chrome for the Zygisk config activity (collapsing bar template). */
@Composable
internal fun ZygiskConfigMiuix(
    title: String,
    snackbarHostState: AppSnackbarHostState,
    floatingActionButton: @Composable () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                                title = title,
                scrollBehavior = scrollBehavior,
                color = if (glassOn) Color.Transparent else MiuixTheme.colorScheme.surface,
                defaultWindowInsetsPadding = true,
            )
        },
        floatingActionButton = floatingActionButton,
        snackbarHost = { AppSnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().then(if (glassOn) topGlass.contentRecorder() else Modifier).nestedScroll(scrollBehavior.nestedScrollConnection)) {
            body(innerPadding, Modifier.scrollEndHaptic())
        }
    }
}
