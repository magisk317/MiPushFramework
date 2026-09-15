package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.ScrollState
import top.yukonga.miuix.kmp.theme.MiuixTheme
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.surfaceBlurContainerColor
import io.github.magisk317.uikit.surface.uiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix chrome for the Settings tab (Architecture A): page-owned miuix Scaffold
 * + collapsing TopAppBar. The pager's scroll-chrome coordination stays wired to
 * the scroll-to-top FAB; the previous header-offset slide is replaced by the
 * miuix scroll behavior on this route.
 */
@Composable
internal fun SettingsMiuix(
    contentPadding: PaddingValues,
    scrollChromeState: ScrollChromeState?,
    scrollState: ScrollState,
    snackbarHostState: AppSnackbarHostState,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                                title = stringResource(R.string.main_settings),
                scrollBehavior = scrollBehavior,
                color = if (glassOn) Color.Transparent else MiuixTheme.colorScheme.surface,
                defaultWindowInsetsPadding = true,
            )
        },
        contentWindowInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().then(if (glassOn) topGlass.contentRecorder() else Modifier).nestedScroll(scrollBehavior.nestedScrollConnection)) {
            body(
                PaddingValues(top = innerPadding.calculateTopPadding()),
                Modifier.scrollEndHaptic(),
            )
            AppSnackbarHost(
                hostState = snackbarHostState,
                bottomPadding = contentPadding.calculateBottomPadding() + 16.dp,
            )
        }
    }
}
