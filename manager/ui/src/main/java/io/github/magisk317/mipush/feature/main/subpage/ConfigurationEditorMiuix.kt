package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.surfaceBlurContainerColor
import io.github.magisk317.uikit.surface.uiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/**
 * Miuix chrome for the configuration editor screen. Static bar by design: the
 * editor uses a weighted fill-remaining layout rather than a collapsing scroll
 * container, matching the previous in-flow bar behavior.
 */
@Composable
internal fun ConfigurationEditorMiuix(
    path: String,
    onBack: () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    Scaffold(
        topBar = {
            SmallTopAppBar(
                                title = path,
                navigationIcon = {
                    AppIconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
                color = Color.Transparent,
                defaultWindowInsetsPadding = true,
            )
        },
        contentWindowInsets = WindowInsets.systemBars
            .union(WindowInsets.displayCutout)
            .only(WindowInsetsSides.Horizontal),
    ) { innerPadding ->
        body(PaddingValues(top = innerPadding.calculateTopPadding()), Modifier.scrollEndHaptic())
    }
}
