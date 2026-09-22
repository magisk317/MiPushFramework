@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.layout.Arrangement
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.theme.spacing
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.common.AppSnackbarHost
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.surface.AppCard
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.OverviewAppInfoCard
import io.github.magisk317.uikit.surface.OverviewDeviceInfoCard
import io.github.magisk317.uikit.surface.OverviewLinksCard
import io.github.magisk317.uikit.R as UiKitR

/**
 * Expressive/Material implementation of the Overview page. Follows the KernelSU
 * `HomeMaterial` model: page-owned `Scaffold` + collapsing top app bar driven by
 * `exitUntilCollapsedScrollBehavior`. The bar itself goes through uikit `AppTopBar`
 * (which, on this path, is the material3 bar plus chrome colors and surface blur).
 */
@Composable
internal fun OverviewExpressive(
    state: OverviewUiState,
    actions: OverviewActions,
    contentPadding: PaddingValues,
    snackbarHostState: AppSnackbarHostState,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val scrollState = rememberScrollState()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            AppTopBar(
                modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                containerColor = if (glassOn) Color.Transparent else MaterialTheme.colorScheme.surface,
                title = stringResource(R.string.app_name),
                actions = {
                    ConnectionStatusIndicator(onClick = actions.onConnectionStatusClick)
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize()
                    .then(if (glassOn) topGlass.contentRecorder() else Modifier)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(scrollState)
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .padding(
                        PaddingValues(
                            start = MaterialTheme.spacing.medium,
                            end = MaterialTheme.spacing.medium,
                            top = innerPadding.calculateTopPadding() + MaterialTheme.spacing.medium,
                            bottom = maxOf(contentPadding.calculateBottomPadding(), bottomInset) +
                                innerPadding.calculateBottomPadding() + 16.dp,
                        ),
                    ),
                verticalArrangement = Arrangement.spacedBy(MaterialTheme.spacing.medium),
            ) {
                AppCard(modifier = Modifier.fillMaxWidth()) {
                    AppStatsDonutSection(appStats = state.appStats)
                }

                OverviewAppInfoCard(
                    appVersionName = state.appVersionName
                        ?: stringResource(UiKitR.string.unknown),
                    runtimeVersionName = state.runtimeVersionName
                        ?: stringResource(UiKitR.string.unknown),
                    appVersionCode = state.appVersionCode,
                    appVersionCodeLabel = stringResource(R.string.commit_info),
                )

                OverviewDeviceInfoCard()

                OverviewLinksCard(
                    onJoinTelegram = actions.onJoinTelegram,
                    onJoinQqChannel = actions.onJoinQqChannel,
                    onSourceCode = actions.onSourceCode,
                    onDonate = actions.onDonate,
                )
            }

            AppSnackbarHost(
                hostState = snackbarHostState,
                bottomPadding = contentPadding.calculateBottomPadding() + 16.dp,
            )
        }
    }
}
