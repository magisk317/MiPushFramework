package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.feature.main.RecentEventListPage
import android.content.Intent
import android.content.Context
import androidx.core.net.toUri
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.WrapText
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import io.github.magisk317.uikit.common.ElevatedSnackbarHost
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import co.touchlab.kermit.Logger
import io.github.magisk317.mipush.manager.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import io.github.magisk317.mipush.common.cache.ApplicationNameCache
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.manager.application.EventDebugJson
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.ManagerStatePolicies
import io.github.magisk317.mipush.manager.application.ManagerEventResult
import io.github.magisk317.mipush.manager.application.ManagerEventType
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.mipush.feature.ui.component.RefreshableLazyColumn
import io.github.magisk317.uikit.surface.DialogAction
import io.github.magisk317.uikit.surface.DialogActionRow
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import io.github.magisk317.uikit.surface.InfoPill
import io.github.magisk317.uikit.surface.OverlayHeaderScaffold
import io.github.magisk317.uikit.surface.WorkspaceTopBarSearchOverlay
import io.github.magisk317.uikit.surface.WorkspaceEmptyState
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.AppBottomSheet
import io.github.magisk317.uikit.preference.StateSwitchItem
import io.github.magisk317.uikit.preference.Item as SettingsItem
import io.github.magisk317.uikit.preference.TextInputDialog
import io.github.magisk317.mipush.feature.ui.theme.spacing
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date
import io.github.magisk317.mipush.main.viewmodel.EventListViewModel
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
import org.koin.compose.viewmodel.koinViewModel

private data class EventGroupForDisplay(
    val packageName: String,
    val appName: String,
    val events: List<EventInfoForDisplay>,
    val latestDate: Date
)

@Composable
internal fun EventGroupList(
    query: String,
    refreshSignal: Int,
    isActive: Boolean,
    contentPadding: PaddingValues,
    viewModel: EventListViewModel,
    selectedTypeFilters: Set<EventTypeFilter>,
    selectedStatusFilters: Set<EventStatusFilter>,
    scrollChromeState: ScrollChromeState? = null,
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
) {
    val context = LocalContext.current
    val groupedItems = remember { mutableStateListOf<EventGroupForDisplay>() }
    val allEvents = remember { mutableStateListOf<EventInfoForDisplay>() }
    var lastId by remember { mutableStateOf<Long?>(null) }
    var hasMore by remember { mutableStateOf(false) }
    var isNeedRefresh by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var initialLoadFailed by remember { mutableStateOf(false) }
    val groupingScope = rememberCoroutineScope()

    fun rebuildGroups() {
        val source = allEvents.toList()
        val typeFilters = selectedTypeFilters
        val statusFilters = selectedStatusFilters
        groupingScope.launch(Dispatchers.Default) {
            val grouped = source
                .asSequence()
                .filter { it.matchesFilters(typeFilters, statusFilters) }
                .groupBy { it.packageName }
                .values
                .map { events ->
                    val sortedEvents = ManagerStatePolicies.newestFirst(events) { it.receiveDate.time }
                    val first = sortedEvents.first()
                    EventGroupForDisplay(
                        packageName = first.packageName,
                        appName = first.appName?.takeIf { it.isNotBlank() } ?: first.packageName,
                        events = sortedEvents,
                        latestDate = first.receiveDate,
                    )
                }
                .let { ManagerStatePolicies.newestFirst(it) { group -> group.latestDate.time } }
            withContext(Dispatchers.Main.immediate) {
                groupedItems.clear()
                groupedItems.addAll(grouped)
            }
        }
    }

    // Cache-first: on open / tab re-enter, restore from the persistent cache
    // (or in-memory snapshot) and paint immediately. Older pages are loaded only
    // when the user scrolls.
    var restoredListKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(isActive, query, refreshSignal) {
        if (!isActive) return@LaunchedEffect
        val listKey = EventListViewModel.eventListRestoreKey(query, "", refreshSignal)
        if (restoredListKey == listKey) return@LaunchedEffect
        withFrameNanos { }
        // Cache-first keeps cold-start UI responsive. Automatic refresh is owned by the
        // background coordinator (XMSF maintenance cycle or standalone periodic worker),
        // and cacheUpdates below applies its result without blocking this page.
        if (viewModel.loadFromCacheIfPresent(query, "", refreshSignal)) {
            val cached = viewModel.getEventListSnapshot(query = query, packageName = "", refreshSignal = refreshSignal)
            if (cached != null) {
                allEvents.clear()
                allEvents.addAll(cached.events)
                lastId = cached.lastId
                hasMore = cached.hasMore
                initialLoadFailed = false
                isNeedRefresh = false
                rebuildGroups()
                restoredListKey = listKey
                return@LaunchedEffect
            }
        }
        val snap = viewModel.getEventListSnapshot(query = query, packageName = "", refreshSignal = refreshSignal)
        if (snap != null) {
            allEvents.clear()
            allEvents.addAll(snap.events)
            lastId = snap.lastId
            hasMore = snap.hasMore
            initialLoadFailed = false
            isNeedRefresh = false
            rebuildGroups()
        } else {
            // Genuine cold start with no cache: let the refresh container pull.
            allEvents.clear()
            lastId = null
            hasMore = true
            initialLoadFailed = false
            isNeedRefresh = true
            rebuildGroups()
        }
        restoredListKey = listKey
    }
    androidx.compose.runtime.LaunchedEffect(selectedTypeFilters, selectedStatusFilters) {
        rebuildGroups()
    }

    LaunchedEffect(isActive, query, refreshSignal) {
        if (!isActive) return@LaunchedEffect
        viewModel.cacheUpdates.collect { key ->
            if (key != viewModel.cacheKey(query, "", refreshSignal)) return@collect
            val cached = viewModel.reloadFromCache(query, "", refreshSignal) ?: return@collect
            allEvents.clear()
            allEvents.addAll(cached.events)
            lastId = cached.lastId
            hasMore = cached.hasMore
            initialLoadFailed = false
            isNeedRefresh = false
            rebuildGroups()
        }
    }

    suspend fun loadNextPage(isRefresh: Boolean) {
        if (isLoading) return
        isLoading = true
        try {
            val events = viewModel.fetchEventsSuspend(isRefresh, lastId, "", query)

            val mergedEvents = EventListViewModel.mergeEventItems(allEvents, events)
            allEvents.clear()
            allEvents.addAll(mergedEvents)
            allEvents.lastOrNull()?.let { lastId = it.id }
            hasMore = hasMore || events.size >= Constants.PAGE_SIZE
            rebuildGroups()
            viewModel.putEventListSnapshot(
                query = query,
                packageName = "",
                refreshSignal = refreshSignal,
                events = allEvents.toList(),
                lastId = lastId,
                hasMore = hasMore,
            )
        } catch (error: RuntimeReadUnavailableException) {
            Logger.withTag("ManagerRuntime").w { "EventList loadNextPage unavailable status=${error.status}" }
            // Do not snapshot empty transport failures; keep previous UI if any.
            if (isRefresh && allEvents.isEmpty()) {
                viewModel.invalidateEventListSnapshot()
                initialLoadFailed = true
            }
        } finally {
            isLoading = false
        }
    }

    val refreshScope = rememberCoroutineScope()
    val doRefresh: (onRefreshed: () -> Unit) -> Unit = { onRefreshed ->
        refreshScope.launch {
            loadNextPage(isRefresh = true)
            withContext(Dispatchers.Main) {
                isNeedRefresh = false
                onRefreshed()
            }
        }
    }
    val doLoadMore: (onRefreshed: () -> Unit) -> Unit = { onRefreshed ->
        if (!hasMore) {
            onRefreshed()
        } else {
            refreshScope.launch {
                loadNextPage(isRefresh = false)
                withContext(Dispatchers.Main) {
                    onRefreshed()
                }
            }
        }
    }
    val isNeedMore: (Int) -> Boolean = { index ->
        hasMore && index >= groupedItems.size - 1
    }

    RefreshableLazyColumn(
        doRefresh = doRefresh,
        isNeedMore = isNeedMore,
        doLoadMore = doLoadMore,
        isNeedRefresh = isNeedRefresh,
        scrollToTopSignal = refreshSignal,
        scrollToTopAfterRefresh = true,
        scrollChromeState = scrollChromeState,
        contentPadding = contentPadding,
        modifier = Modifier,
        listState = listState,
    ) {
        if (groupedItems.isEmpty() && isLoading) {
            item {
                InitialEventLoadState(modifier = Modifier.fillMaxWidth())
            }
        } else if (groupedItems.isEmpty() && !isLoading) {
            item {
                if (initialLoadFailed) {
                    EventLoadFailedState(
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    EmptyEventState(
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        } else {
            items(groupedItems, key = { it.packageName }) { group ->
                val updatedAt = friendlyDateString(
                    group.latestDate,
                    Date(),
                    context
                )
                WorkspaceListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    onClick = {
                        context.startActivity(
                            Intent(context, RecentEventListPage::class.java)
                                .setData(group.packageName.toUri()),
                        )
                    },
                    leadingContent = {
                        AppIconImage(
                            packageName = group.packageName,
                            modifier = Modifier.size(48.dp),
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                ) {
                    Text(
                        group.appName,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        group.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        InfoPill(
                            text = pluralStringResource(
                                R.plurals.recent_activity_group_count,
                                group.events.size,
                                group.events.size,
                            ),
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        InfoPill(
                            text = stringResource(R.string.recent_activity_updated_at, updatedAt),
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
        }
    }
}
