package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.surfaceBlurContainerColor
import io.github.magisk317.uikit.surface.uiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Miuix chrome for the configurations list screen. */
@Composable
internal fun ConfigurationsMiuix(
    onBack: (() -> Unit)?,
    contentPadding: PaddingValues,
    scrollChromeState: ScrollChromeState?,
    listState: LazyListState,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                                title = stringResource(R.string.main_configs),
                navigationIcon = {
                    onBack?.let { back ->
                        AppIconButton(onClick = back) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = stringResource(android.R.string.cancel),
                            )
                        }
                    }
                },
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
        }
    }
}
