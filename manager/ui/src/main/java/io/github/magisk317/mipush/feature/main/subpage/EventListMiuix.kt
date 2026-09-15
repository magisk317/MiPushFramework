package io.github.magisk317.mipush.feature.main.subpage

import androidx.compose.foundation.background
import io.github.magisk317.uikit.surface.uiKitSurfaceGlassSample
import io.github.magisk317.uikit.surface.rememberUiKitGlassTopBar
import io.github.magisk317.uikit.theme.LocalUiKitSurfaceBlur
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.layout.Box
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
import io.github.magisk317.uikit.common.AppSnackbarHostState
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.WorkspaceSearchAction
import io.github.magisk317.uikit.surface.WorkspaceSearchField
import io.github.magisk317.uikit.surface.chromeSurfaceColor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * Miuix chrome for the recent-activity tab (KernelSU `HomeMiuix` model): native
 * miuix `Scaffold` + collapsing `TopAppBar` driven by `MiuixScrollBehavior`;
 * the title-bar search icon swaps the bar slot for the expanded search row.
 */
@Composable
internal fun EventListMiuix(
    state: EventListUiState,
    actions: EventListActions,
    snackbarHostState: AppSnackbarHostState,
    listState: LazyListState,
    scrollScope: CoroutineScope,
    scrollChromeState: ScrollChromeState?,
    contentBottomPadding: Dp,
    body: @Composable (PaddingValues) -> Unit,
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
                        top = with(density) { topBarHeightPx.toDp() },
                        bottom = contentBottomPadding + 28.dp,
                    ),
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
                            title = state.heroTitle,
                            actions = {
                                if (state.showSettings) {
                                    ApplicationHeaderSettingsAction(onClick = actions.onSettingsClick)
                                }
                                WorkspaceSearchAction(
                                    active = false,
                                    contentDescription = placeholder,
                                    onClick = { searchState.toggle() },
                                )
                            },
                            scrollBehavior = scrollBehavior,
                            color = if (glassOn) Color.Transparent else MiuixTheme.colorScheme.surface,
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
        EventTabSnackbarHost(
            snackbarHostState = snackbarHostState,
            contentBottomPadding = contentBottomPadding,
        )
    }
}
