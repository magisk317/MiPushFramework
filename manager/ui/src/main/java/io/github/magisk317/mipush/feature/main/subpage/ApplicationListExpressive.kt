@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Color
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magisk317.uikit.surface.DoubleTapToTopOverlay
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import io.github.magisk317.uikit.surface.WorkspaceSearchField
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import io.github.magisk317.uikit.surface.WorkspaceSearchAction
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Expressive/Material chrome for the application list tab (full KernelSU
 * `SuperUserPagerMaterial` model): page-owned material3 `Scaffold` +
 * `exitUntilCollapsed` top bar with the [MaterialSearchBarPill], summary and
 * filter pills fixed under it; expanded search swaps the bar slot for the
 * (unchanged, verified) search row.
 */
@Composable
internal fun ApplicationListExpressive(
    state: ApplicationListUiState,
    actions: ApplicationListActions,
    listState: LazyListState,
    scrollScope: CoroutineScope,
    scrollChromeState: ScrollChromeState?,
    contentBottomPadding: Dp,
    body: @Composable (PaddingValues, AppRowStyle) -> Unit,
) {
    val searchState = state.searchState
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    val placeholder = stringResource(android.R.string.search_go)
    val statusBarTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val topGlass = rememberUiKitGlassTopBar()
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Box(modifier = Modifier.fillMaxWidth()) {
                if (searchState.expanded) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(chromeSurfaceColor())
                            .windowInsetsPadding(WindowInsets.statusBars)
                            .padding(horizontal = 4.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        IconButton(onClick = actions.onCloseSearch) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        WorkspaceSearchField(
                            query = searchState.query,
                            placeholder = placeholder,
                            modifier = Modifier.weight(1f),
                            autoFocus = true,
                            onValueChange = { searchState.updateQuery(it) },
                        )
                    }
                } else {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        TopAppBar(
                            modifier = Modifier.uiKitSurfaceGlassSample(topGlass),
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = if (glassOn) Color.Transparent else MaterialTheme.colorScheme.surface,
                            ),
                            title = {
                                Text(
                                    text = stringResource(R.string.app_list_hero_title),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            actions = {
                                ApplicationHeaderSettingsAction(onClick = actions.onSettingsClick)
                                WorkspaceSearchAction(
                                    active = false,
                                    contentDescription = placeholder,
                                    onClick = { searchState.toggle() },
                                )
                            },
                            scrollBehavior = scrollBehavior,
                            windowInsets = WindowInsets.statusBars,
                        )
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(chromeSurfaceColor()),
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    start = MaterialTheme.spacing.medium,
                                    end = MaterialTheme.spacing.medium,
                                    bottom = MaterialTheme.spacing.small,
                                ),
                            ) {
                                ApplicationHeaderPills(
                                    stats = state.stats,
                                    query = state.currentQuery,
                                    filterMode = state.filterMode,
                                    showSystemApps = state.showSystemApps,
                                )
                            }
                        }
                    }
                }
                DoubleTapToTopOverlay(
                    onDoubleTap = { scrollScope.launch { listState.animateScrollToItem(0) } },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(
                            start = 72.dp,
                            top = statusBarTop,
                            end = 112.dp,
                        )
                        .fillMaxWidth()
                        .height(64.dp),
                )
            }
        },
    ) { innerPadding ->
        Box(modifier = Modifier.fillMaxSize().then(if (glassOn) topGlass.contentRecorder() else Modifier)) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                body(
                    PaddingValues(
                        top = innerPadding.calculateTopPadding(),
                        bottom = contentBottomPadding + 28.dp,
                    ),
                    ExpressiveRowStyle,
                )
            }
            ScrollToTopFAB(
                listState,
                visible = scrollChromeState?.isChromeVisible != true,
                extraBottomPadding = contentBottomPadding,
            )
        }
    }
}

/** Expressive row style: unchanged full-bleed rows with dividers. */
private val ExpressiveRowStyle = AppRowStyle(divider = true) { content -> content() }
