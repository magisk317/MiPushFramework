@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.wizard

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import io.github.magisk317.uikit.surface.AppTopBar

/** Expressive/Material chrome for the permission wizard (collapsing bar). */
@Composable
internal fun RequestPermissionExpressive(
    title: String,
    navigationIcon: @Composable () -> Unit,
    bottomBar: @Composable () -> Unit,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        topBar = {
            AppTopBar(
                title = title,
                navigationIcon = navigationIcon,
                scrollBehavior = scrollBehavior,
                windowInsets = WindowInsets.statusBars,
            )
        },
        bottomBar = bottomBar,
    ) { innerPadding ->
        body(innerPadding, Modifier.nestedScroll(scrollBehavior.nestedScrollConnection))
    }
}
