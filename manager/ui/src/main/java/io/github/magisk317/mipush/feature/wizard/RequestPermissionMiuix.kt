package io.github.magisk317.mipush.feature.wizard

import androidx.compose.foundation.layout.Box
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.surfaceBlurContainerColor
import io.github.magisk317.uikit.surface.uiKitSurfaceBlur
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.utils.scrollEndHaptic

/** Miuix chrome for the permission wizard (collapsing bar template). */
@Composable
internal fun RequestPermissionMiuix(
    title: String,
    navigationIcon: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = MiuixScrollBehavior()
    Scaffold(
        topBar = {
            TopAppBar(
                                title = title,
                navigationIcon = navigationIcon,
                scrollBehavior = scrollBehavior,
                color = Color.Transparent,
                defaultWindowInsetsPadding = true,
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().nestedScroll(scrollBehavior.nestedScrollConnection)) {
            body(innerPadding, Modifier.scrollEndHaptic())
        }
    }
}
