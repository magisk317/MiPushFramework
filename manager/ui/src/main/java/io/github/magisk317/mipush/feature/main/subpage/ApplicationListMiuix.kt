package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.background
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.feature.ui.component.DoubleTapToTopOverlay
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.WorkspaceSearchAction
import io.github.magisk317.uikit.surface.WorkspaceSearchField
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar

/**
 * Miuix chrome for the application list tab (KernelSU `SuperUserMiuix` model):
 * native miuix `Scaffold` + collapsing `TopAppBar` driven by
 * `MiuixScrollBehavior`. The summary and the filter pills live in the bar's
 * `bottomContent`, so the large title and the hero slide down on pull-down and
 * collapse up on scroll exactly like HyperOS; the title-bar search icon swaps
 * the bar slot for the expanded search row. Renders from
 * [ApplicationListUiState] / [ApplicationListActions].
 */
@Composable
internal fun ApplicationListMiuix(
    state: ApplicationListUiState,
    actions: ApplicationListActions,
    listState: LazyListState,
    scrollScope: CoroutineScope,
    scrollChromeState: ScrollChromeState?,
    contentBottomPadding: Dp,
    body: @Composable (PaddingValues, AppRowStyle) -> Unit,
) {
    val searchState = state.searchState
    val scrollBehavior = MiuixScrollBehavior()
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val placeholder = stringResource(android.R.string.search_go)
    Box(modifier = Modifier.fillMaxSize()) {
    val topGlass = rememberUiKitGlassTopBar()
    val density = LocalDensity.current
    var topBarHeightPx by remember { mutableIntStateOf(0) }
    val glassOn = LocalUiKitSurfaceBlur.current.usesBackdrop
    Scaffold(
            topBar = {},
            contentWindowInsets = WindowInsets.systemBars
                .union(WindowInsets.displayCutout)
                .only(WindowInsetsSides.Horizontal),
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (glassOn) topGlass.contentRecorder() else Modifier)
                    .nestedScroll(scrollBehavior.nestedScrollConnection),
            ) {
                body(
                    PaddingValues(
                        top = with(density) { topBarHeightPx.toDp() } + MaterialTheme.spacing.small,
                        bottom = contentBottomPadding + 28.dp,
                    ),
                    MiuixRowStyle,
                )
            }
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .fillMaxWidth()
                    .onSizeChanged { topBarHeightPx = it.height }
                    .then(if (glassOn) Modifier.uiKitSurfaceGlassSample(topGlass) else Modifier),
            ) {
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
                        TopAppBar(
                            title = stringResource(R.string.app_list_hero_title),
                            actions = {
                                ApplicationHeaderSettingsAction(onClick = actions.onSettingsClick)
                                WorkspaceSearchAction(
                                    active = false,
                                    contentDescription = placeholder,
                                    onClick = { searchState.toggle() },
                                )
                            },
                            scrollBehavior = scrollBehavior,
                            color = if (glassOn) Color.Transparent else MiuixTheme.colorScheme.surface,
                            bottomContent = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
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
                            },
                        )
                    }
                    DoubleTapToTopOverlay(
                        onDoubleTap = { scrollScope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(
                                start = 72.dp,
                                top = topInset,
                                end = 112.dp,
                            )
                            .fillMaxWidth()
                            .height(64.dp),
                    )
                }
            }
        }
    }
}

/** Miuix row style: gapped card rows (12dp side margins, 6dp bottom gap), no dividers — the KernelSU SimpleAppItem rhythm. */
private val MiuixRowStyle = AppRowStyle(divider = false) { content ->
    Box(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .padding(bottom = 6.dp),
    ) {
        content()
    }
}
