package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.common.R as CommonR
import io.github.magisk317.mipush.feature.main.RecentEventListPage
import android.content.Intent
import android.net.Uri
import android.content.Context
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
import androidx.compose.ui.res.painterResource
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

fun EventInfoForDisplay.composeKey(): String {
    if (id > 0L) return "id:$id"
    return "legacy:${packageName}:${receiveDate.time}:${title.hashCode()}:${content.hashCode()}"
}

private fun MutableList<EventInfoForDisplay>.appendDistinct(itemsToAppend: List<EventInfoForDisplay>) {
    if (itemsToAppend.isEmpty()) return
    val existing = asSequence().map { it.composeKey() }.toHashSet()
    for (item in itemsToAppend) {
        if (existing.add(item.composeKey())) {
            add(item)
        }
    }
}


@Composable
internal fun EventList(
    onClick: (EventInfoForDisplay) -> Unit,
    getEvents: suspend (isRefresh: Boolean) -> List<EventInfoForDisplay>,
    query: String,
    packageName: String,
    refreshSignal: Int = 0,
    isActive: Boolean = true,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    selectedTypeFilters: Set<EventTypeFilter> = emptySet(),
    selectedStatusFilters: Set<EventStatusFilter> = emptySet(),
    snackbarHostState: SnackbarHostState,
    viewModel: EventListViewModel,
    scrollChromeState: ScrollChromeState? = null,
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val recentActivityDeletedMessage = stringResource(R.string.recent_activity_deleted)
    val recentActivityDeleteFailedMessage = stringResource(R.string.recent_activity_delete_failed)
    val recentActivityRestoreFailedMessage = stringResource(R.string.recent_activity_restore_failed)
    val actionUndoLabel = stringResource(R.string.action_undo)
    val items = remember {
        mutableStateListOf<EventInfoForDisplay>()
    }

    val refreshScope = rememberCoroutineScope()
    val actionScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(false) }
    var isNeedRefresh by remember { mutableStateOf(false) }
    var initialLoadFailed by remember { mutableStateOf(false) }
    fun persistSnapshot() {
        viewModel.putEventListSnapshot(
            query = query,
            packageName = packageName,
            refreshSignal = refreshSignal,
            events = items.toList(),
            lastId = items.lastOrNull()?.id,
            hasMore = hasMore,
        )
    }

    // Cache-first: on open / tab re-enter, restore from the persistent store
    // (or in-memory snapshot) and paint immediately. Older pages are loaded only
    // when the user scrolls.
    var restoredListKey by rememberSaveable { mutableStateOf<String?>(null) }
    LaunchedEffect(isActive, query, packageName, refreshSignal) {
        if (!isActive) return@LaunchedEffect
        val listKey = viewModel.cacheKey(query, packageName, refreshSignal)
        if (restoredListKey == listKey) return@LaunchedEffect
        if (viewModel.loadFromCacheIfPresent(query, packageName, refreshSignal)) {
            val cached = viewModel.getEventListSnapshot(
                query = query,
                packageName = packageName,
                refreshSignal = refreshSignal,
            )
            if (cached != null) {
                items.clear()
                items.appendDistinct(cached.events)
                hasMore = cached.hasMore
                initialLoadFailed = false
                isNeedRefresh = false
                restoredListKey = listKey
                return@LaunchedEffect
            }
        }
        val snap = viewModel.getEventListSnapshot(
            query = query,
            packageName = packageName,
            refreshSignal = refreshSignal,
        )
        if (snap != null) {
            items.clear()
            items.appendDistinct(snap.events)
            hasMore = snap.hasMore
            initialLoadFailed = false
            isNeedRefresh = false
        } else {
            items.clear()
            hasMore = true
            initialLoadFailed = false
            isNeedRefresh = true
        }
        restoredListKey = listKey
    }

    LaunchedEffect(isActive, query, packageName, refreshSignal) {
        if (!isActive) return@LaunchedEffect
        viewModel.cacheUpdates.collect { key ->
            if (key != viewModel.cacheKey(query, packageName, refreshSignal)) return@collect
            val cached = viewModel.reloadFromCache(query, packageName, refreshSignal) ?: return@collect
            items.clear()
            items.appendDistinct(cached.events)
            hasMore = cached.hasMore
            initialLoadFailed = false
            isNeedRefresh = false
        }
    }

    val doLoadMore: (onRefreshed: () -> Unit) -> Unit = doLoadMore@{ onRefreshed ->
        if (isLoading || !hasMore) {
            onRefreshed()
            return@doLoadMore
        }
        isLoading = true
        refreshScope.launch {
            try {
                val loaded = getEvents(false)
                withContext(Dispatchers.Main) {
                    items.appendDistinct(loaded)
                    hasMore = loaded.size >= Constants.PAGE_SIZE
                    persistSnapshot()
                    isLoading = false
                    onRefreshed()
                }
            } catch (error: RuntimeReadUnavailableException) {
                Logger.withTag("ManagerRuntime").w { "EventList doLoadMore unavailable status=${error.status}" }
                withContext(Dispatchers.Main) {
                    isLoading = false
                    onRefreshed()
                }
            }
        }
    }
    val doRefresh: (onRefreshed: () -> Unit) -> Unit = doRefresh@{ onRefreshed ->
        if (isLoading) {
            onRefreshed()
            return@doRefresh
        }
        isLoading = true
        refreshScope.launch {
            try {
                val elements = getEvents(true)
                withContext(Dispatchers.Main) {
                    val mergedItems = EventListViewModel.mergeEventItems(items, elements)
                    items.clear()
                    items.addAll(mergedItems)
                    hasMore = hasMore || elements.size >= Constants.PAGE_SIZE
                    persistSnapshot()
                    isLoading = false
                    isNeedRefresh = false
                    onRefreshed()
                }
            } catch (error: RuntimeReadUnavailableException) {
                Logger.withTag("ManagerRuntime").w { "EventList doRefresh unavailable status=${error.status}" }
                withContext(Dispatchers.Main) {
                    if (items.isEmpty()) {
                        viewModel.invalidateEventListSnapshot()
                        initialLoadFailed = true
                    }
                    isLoading = false
                    isNeedRefresh = false
                    onRefreshed()
                }
            }
        }
    }

    val isNeedMore: (Int) -> Boolean = { hasMore && !isLoading && it >= items.size - 10 }
    val filteredItems = items.filter { it.matchesFilters(selectedTypeFilters, selectedStatusFilters) }

    fun deleteEventWithUndo(item: EventInfoForDisplay) {
        val key = item.composeKey()
        val insertAt = items.indexOfFirst { it.composeKey() == key }.coerceAtLeast(0)
        items.removeAll { it.composeKey() == key }
        persistSnapshot()

        actionScope.launch {
            if (!viewModel.deleteEvent(item)) {
                val idx = insertAt.coerceIn(0, items.size)
                items.add(idx, item)
                persistSnapshot()
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(recentActivityDeleteFailedMessage)
                return@launch
            }
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = recentActivityDeletedMessage,
                actionLabel = actionUndoLabel,
                duration = SnackbarDuration.Indefinite,
            )
            if (result == SnackbarResult.ActionPerformed) {
                val restored = viewModel.restoreEvent(item)
                if (restored != null) {
                    val idx = insertAt.coerceIn(0, items.size)
                    items.add(idx, restored)
                    persistSnapshot()
                } else {
                    snackbarHostState.showSnackbar(recentActivityRestoreFailedMessage)
                }
            }
        }
    }

    RefreshableLazyColumn(
        doRefresh,
        isNeedMore,
        doLoadMore,
        isNeedRefresh,
        scrollToTopSignal = refreshSignal,
        scrollToTopAfterRefresh = true,
        scrollChromeState = scrollChromeState,
        contentPadding = contentPadding,
        modifier = Modifier,
        listState = listState,
    ) {
        if (filteredItems.isEmpty() && isLoading) {
            item {
                InitialEventLoadState(modifier = Modifier.fillMaxWidth())
            }
        } else if (filteredItems.isEmpty() && !isLoading) {
            item {
                if (initialLoadFailed) {
                    EventLoadFailedState(modifier = Modifier.fillMaxWidth())
                } else {
                    EmptyEventState(modifier = Modifier.fillMaxWidth())
                }
            }
        } else {
            items(filteredItems, key = { it.composeKey() }) {
                SwipeToDeleteEventItem(
                    item = it,
                    onDelete = ::deleteEventWithUndo,
                    onClick = onClick,
                )
            }
        }
    }
}

@Composable
private fun SwipeToDeleteEventItem(
    item: EventInfoForDisplay,
    onDelete: (EventInfoForDisplay) -> Unit,
    onClick: (EventInfoForDisplay) -> Unit,
) {
    val dismissState = rememberSwipeToDismissBoxState()
    var observedInitialValue by remember { mutableStateOf(false) }
    var deleteTriggered by remember { mutableStateOf(false) }

    // A restored item can keep a non-settled dismiss state when LazyColumn
    // reuses its keyed composition. Reset that initial state without treating
    // it as a new user gesture.
    LaunchedEffect(dismissState.currentValue) {
        if (!observedInitialValue) {
            observedInitialValue = true
            if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
            }
            return@LaunchedEffect
        }
        if (!deleteTriggered && dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            deleteTriggered = true
            onDelete(item)
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true,
        backgroundContent = {
            val fromEnd = dismissState.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp)
                    .background(
                        color = MaterialTheme.colorScheme.errorContainer,
                        shape = RoundedCornerShape(8.dp),
                    )
                    .padding(horizontal = 24.dp),
                contentAlignment = if (fromEnd) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Text(
                    text = stringResource(R.string.action_delete),
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        },
    ) {
        EventItem(item, onClick)
    }
}

@Composable
private fun EventItem(
    item: EventInfoForDisplay,
    onClick: (EventInfoForDisplay) -> Unit,
) {
    val disabled = item.isDisabled()
    val denied = item.event.result != ManagerEventResult.OK
    val appName = item.appName?.takeIf { it.isNotBlank() } ?: item.packageName
    val titleText = item.title
    val metaLine = if (item.channel.isNotBlank()) {
        "$appName · ${item.channel}"
    } else {
        appName
    }
    val surface = MaterialTheme.colorScheme.surface
    val containerColor = when {
        denied -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.18f).compositeOver(surface)
        else -> surface
    }

    WorkspaceListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        containerColor = containerColor,
        onClick = { onClick(item) },
        leadingContent = {
            AppIconImage(item.packageName, item.appName, modifier = Modifier.size(40.dp))
        },
        trailingContent = null,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = titleText,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.size(MaterialTheme.spacing.small))
            Text(
                text = Instant.ofEpochMilli(item.receiveDate.time).atZone(ZoneId.systemDefault()).format(receiveDateTimeFormatter),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = metaLine,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                modifier = Modifier.weight(1f),
            )
            if (disabled) {
                InfoPill(
                    text = stringResource(R.string.notification_channels_disabled_badge),
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            } else if (denied) {
                InfoPill(
                    text = stringResource(R.string.recent_activity_filter_status_denied),
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
        }
        Text(
            text = item.content,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
internal fun DeleteCountdownSnackbar(data: androidx.compose.material3.SnackbarData) {
    var secondsLeft by remember { mutableIntStateOf(5) }
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(data) {
        repeat(5) {
            delay(1_000L)
            secondsLeft--
        }
        if (!settled) {
            data.dismiss()
        }
    }
    Snackbar(
        action = {
            TextButton(onClick = {
                settled = true
                data.performAction()
                data.dismiss()
            }) {
                Text("${data.visuals.actionLabel} (${secondsLeft.coerceAtLeast(0)}s)")
            }
        },
    ) {
        Text(data.visuals.message)
    }
}
