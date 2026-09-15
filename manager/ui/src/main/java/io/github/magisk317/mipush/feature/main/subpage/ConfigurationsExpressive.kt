@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.AppIconButton
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import androidx.compose.material3.Icon

/** Expressive/Material chrome for the configurations list screen (collapsing bar). */
@Composable
internal fun ConfigurationsExpressive(
    onBack: (() -> Unit)?,
    contentPadding: PaddingValues,
    scrollChromeState: ScrollChromeState?,
    listState: LazyListState,
    body: @Composable (PaddingValues, Modifier) -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
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
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()) {
            body(
                PaddingValues(top = innerPadding.calculateTopPadding()),
                Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            )
            ScrollToTopFAB(
                listState,
                visible = scrollChromeState?.isChromeVisible != true,
                extraBottomPadding = contentPadding.calculateBottomPadding(),
            )
        }
    }
}
