@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
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
import android.util.Log
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.manager.R
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import io.github.magisk317.mipush.common.cache.ApplicationNameCache
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.manager.EventDebugJson
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.ManagerEventResult
import io.github.magisk317.mipush.common.manager.ManagerEventType
import io.github.magisk317.mipush.common.notification.MockReplayOutcome
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

private val receiveDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

@Composable
fun EventList(
    query: String = "",
    packageName: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    groupByApp: Boolean = false,
    isActive: Boolean = true,
    viewModel: EventListViewModel = koinViewModel(),
    settingsViewModel: SettingsViewModel = koinViewModel(),
    scrollChromeState: ScrollChromeState? = null,
) {
    Page {
        Box(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        var clickedEvent by remember { mutableStateOf<EventInfoForDisplay?>(null) }
        var currentQuery by rememberSaveable(query) { mutableStateOf(query) }
        var preferenceRefreshSignal by rememberSaveable { mutableStateOf(0) }
        val effectiveRefreshSignal = refreshSignal + preferenceRefreshSignal
        var searchExpanded by rememberSaveable(query) { mutableStateOf(query.isNotBlank()) }
        var selectedTypeFilters by remember { mutableStateOf(emptySet<EventTypeFilter>()) }
        var selectedStatusFilters by remember { mutableStateOf(emptySet<EventStatusFilter>()) }
        var groupMode by rememberSaveable(groupByApp, packageName) { mutableStateOf(groupByApp) }
        val showGroupedByApp = packageName.isEmpty() && groupMode
        val snackbarHostState = remember { SnackbarHostState() }
        val scope = rememberCoroutineScope()
        val eventRetentionDays by viewModel.eventRetentionDays.collectAsState()
        val showAllEvents by settingsViewModel.showAllEvents.collectAsState()
        var showListSettingsSheet by rememberSaveable { mutableStateOf(false) }
        var showRetentionDialog by rememberSaveable { mutableStateOf(false) }
        var showCleanupDialog by rememberSaveable { mutableStateOf(false) }
        val retentionError = stringResource(R.string.event_retention_dialog_error)
        val retentionUpdateFailed = stringResource(
            R.string.settings_runtime_preference_update_failed,
            stringResource(R.string.recent_activity_action_retention),
        )
        val resolvedTitle = remember(packageName) {
            if (packageName.isBlank()) {
                null
            } else {
                ApplicationNameCache.getAppName(context, packageName).toString()
                    .takeIf { it.isNotBlank() }
                    ?.takeUnless { it == packageName }
                    ?: packageName
            }
        }
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val searchActive = searchExpanded || currentQuery.isNotBlank()
        val topOverlayHeight = topInset + if (searchActive) 152.dp else 64.dp

        clickedEvent?.let {
            EventDetailsDialog(it, viewModel = viewModel) { clickedEvent = null }
        }

        OverlayHeaderScaffold(
            fallbackTopPadding = topOverlayHeight,
            bottomPadding = contentPadding.calculateBottomPadding() + 28.dp,
            headerOffsetY = scrollChromeState?.animatedHeaderOffsetY ?: 0f,
            onHeaderHeightChanged = { scrollChromeState?.headerHeightPx = it.toFloat() },
            overlayModifier = Modifier
                .fillMaxWidth(),
            content = { listPadding ->
                if (showGroupedByApp) {
                    EventGroupList(
                        query = currentQuery,
                        refreshSignal = effectiveRefreshSignal,
                        isActive = isActive,
                        contentPadding = PaddingValues(
                            top = listPadding.calculateTopPadding() + 8.dp,
                            bottom = listPadding.calculateBottomPadding(),
                        ),
                        viewModel = viewModel,
                        selectedTypeFilters = selectedTypeFilters,
                        selectedStatusFilters = selectedStatusFilters,
                        scrollChromeState = scrollChromeState,
                        listState = listState,
                    )
                } else {
                    EventList(
                        onClick = { clickedEvent = it },
                        getEvents = { isRefresh ->
                            val lastId = if (isRefresh) {
                                null
                            } else {
                                viewModel.getEventListSnapshot(
                                    query = currentQuery,
                                    packageName = packageName,
                                    refreshSignal = effectiveRefreshSignal,
                                )?.lastId
                            }
                            viewModel.fetchEventsSuspend(isRefresh, lastId, packageName, currentQuery)
                        },
                        query = currentQuery,
                        packageName = packageName,
                        refreshSignal = effectiveRefreshSignal,
                        isActive = isActive,
                        contentPadding = PaddingValues(
                            top = listPadding.calculateTopPadding() + 8.dp,
                            bottom = listPadding.calculateBottomPadding(),
                        ),
                        selectedTypeFilters = selectedTypeFilters,
                        selectedStatusFilters = selectedStatusFilters,
                        snackbarHostState = snackbarHostState,
                        viewModel = viewModel,
                        scrollChromeState = scrollChromeState,
                        listState = listState,
                    )
                }
            },
            overlay = {
                WorkspaceTopBarSearchOverlay(
                    title = if (packageName.isNotEmpty()) {
                        resolvedTitle ?: packageName
                    } else {
                        stringResource(R.string.recent_activity_title)
                    },
                    searchQuery = currentQuery,
                    searchPlaceholder = stringResource(android.R.string.search_go),
                    searchVisible = searchActive,
                    searchActionContentDescription = stringResource(R.string.action_search),
                    onSearchActionClick = { searchExpanded = !searchExpanded },
                    actions = {
                        if (packageName.isEmpty()) {
                            IconButton(onClick = { showListSettingsSheet = true }) {
                                Icon(
                                    painter = painterResource(CommonR.drawable.ic_settings_black_24dp),
                                    contentDescription = stringResource(R.string.action_list_settings),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onSearchChange = { currentQuery = it },
                )
            }
        )
            val snackbarBottomPadding = contentPadding.calculateBottomPadding() +
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                MaterialTheme.spacing.medium
            ElevatedSnackbarHost(
                hostState = snackbarHostState,
                bottomPadding = snackbarBottomPadding,
                modifier = Modifier.padding(
                    start = MaterialTheme.spacing.medium,
                    end = MaterialTheme.spacing.medium,
                ),
                snackbar = { data -> DeleteCountdownSnackbar(data) },
            )
            ScrollToTopFAB(
                listState = listState,
                visible = snackbarHostState.currentSnackbarData == null && scrollChromeState?.isChromeVisible != true,
                extraBottomPadding = 80.dp,
            )

            // 列表设置：与 xinyi / xsmscode 记录页拉齐，收进一个设置图标 → 底部 sheet。
            AppBottomSheet(
                show = showListSettingsSheet,
                onDismissRequest = { showListSettingsSheet = false },
                title = stringResource(R.string.action_list_settings),
            ) {
                val showAllEventsTitle = stringResource(R.string.settings_show_all_events)
                val showAllEventsUpdateFailed = stringResource(
                    R.string.settings_runtime_preference_update_failed,
                    showAllEventsTitle,
                )
                StateSwitchItem(
                    title = showAllEventsTitle,
                    summary = stringResource(R.string.settings_show_all_events_summary),
                    checked = showAllEvents,
                ) { enabled ->
                    settingsViewModel.setShowAllEvents(enabled) { success ->
                        if (!success) {
                            scope.launch {
                                snackbarHostState.showSnackbar(showAllEventsUpdateFailed)
                            }
                        } else {
                            // This preference changes which rows the runtime returns. Force the
                            // visible query to reload instead of only warming the cache, otherwise
                            // a non-empty list keeps showing the previous filter until re-entry.
                            preferenceRefreshSignal++
                        }
                    }
                }
                StateSwitchItem(
                    title = stringResource(R.string.recent_activity_action_group_by_app),
                    summary = stringResource(R.string.recent_activity_group_by_app_summary),
                    checked = groupMode,
                ) { checked ->
                    groupMode = checked
                }
                SettingsItem(
                    title = stringResource(R.string.recent_activity_action_retention),
                    summary = stringResource(R.string.event_retention_summary, eventRetentionDays),
                ) {
                    showListSettingsSheet = false
                    showRetentionDialog = true
                }
                SettingsItem(
                    title = stringResource(R.string.event_cleanup_title),
                    summary = stringResource(R.string.event_cleanup_summary),
                ) {
                    showListSettingsSheet = false
                    showCleanupDialog = true
                }
            }

            if (showRetentionDialog) {
                TextInputDialog(
                    title = stringResource(R.string.event_retention_dialog_title),
                    initialValue = eventRetentionDays.toString(),
                    selectAllOnOpen = true,
                    supportingText = stringResource(R.string.event_retention_dialog_hint),
                    onDismiss = { showRetentionDialog = false },
                    validator = { input ->
                        val days = input.toIntOrNull()
                        if (days == null || days < 1) retentionError else null
                    },
                ) { input ->
                    input.toIntOrNull()?.takeIf { it >= 1 }?.let { days ->
                        viewModel.setEventRetentionDays(days) { success ->
                            if (success) {
                                showRetentionDialog = false
                            } else {
                                scope.launch {
                                    snackbarHostState.showSnackbar(retentionUpdateFailed)
                                }
                            }
                        }
                    }
                }
            }

            if (showCleanupDialog) {
                val cleanupScope = rememberCoroutineScope()
                val cleanupDoneTemplate = stringResource(R.string.event_cleanup_done)
                val cleanupNoneMessage = stringResource(R.string.event_cleanup_none)
                val cleanupFailedMessage = stringResource(R.string.event_cleanup_failed)
                EventCleanupCalendarDialog(
                    viewModel = viewModel,
                    onDismiss = { showCleanupDialog = false },
                    onCleaned = { deleted ->
                        cleanupScope.launch {
                            snackbarHostState.currentSnackbarData?.dismiss()
                            snackbarHostState.showSnackbar(
                                if (deleted > 0) {
                                    String.format(cleanupDoneTemplate, deleted)
                                } else {
                                    cleanupNoneMessage
                                },
                            )
                        }
                    },
                    onCleanupFailed = {
                        cleanupScope.launch {
                            snackbarHostState.showSnackbar(cleanupFailedMessage)
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun EventHeaderPills(
    showGroupedByApp: Boolean,
    query: String,
    packageName: String,
    selectedTypeFilters: Set<EventTypeFilter>,
    selectedStatusFilters: Set<EventStatusFilter>,
) {
    val activeFilterCount = selectedTypeFilters.size + selectedStatusFilters.size
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InfoPill(
            text = stringResource(
                if (showGroupedByApp) {
                    R.string.recent_activity_mode_grouped
                } else {
                    R.string.recent_activity_mode_stream
                }
            ),
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
            contentColor = MaterialTheme.colorScheme.primary,
        )
        if (query.isNotBlank()) {
            InfoPill(
                text = "${stringResource(R.string.action_search)} · $query",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        if (activeFilterCount > 0) {
            InfoPill(
                text = "${stringResource(R.string.recent_activity_filter_prefix)} · $activeFilterCount",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (packageName.isNotBlank()) {
            InfoPill(
                text = packageName,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}


private data class EventGroupForDisplay(
    val packageName: String,
    val appName: String,
    val events: List<EventInfoForDisplay>,
    val latestDate: Date
)

@Composable
private fun EventFilters(
    expanded: Boolean,
    selectedTypeFilters: Set<EventTypeFilter>,
    selectedStatusFilters: Set<EventStatusFilter>,
    onExpandedChange: () -> Unit,
    onTypeFiltersChange: (Set<EventTypeFilter>) -> Unit,
    onStatusFiltersChange: (Set<EventStatusFilter>) -> Unit,
    showToggleAction: Boolean = true,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.recent_activity_filter_prefix),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showToggleAction) {
                TextButton(onClick = onExpandedChange) {
                    Text(
                        text = stringResource(
                            if (expanded) {
                                R.string.action_collapse
                            } else {
                                R.string.action_expand
                            }
                        ),
                    )
                }
            }
        }
        if (!expanded) {
            return@Column
        }
        Text(
            text = stringResource(R.string.recent_activity_filter_type_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedTypeFilters.isEmpty(),
                onClick = { onTypeFiltersChange(emptySet()) },
                label = { Text(stringResource(R.string.recent_activity_filter_type_all)) },
            )
            EventTypeFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter in selectedTypeFilters,
                    onClick = {
                        onTypeFiltersChange(selectedTypeFilters.toggle(filter))
                    },
                    label = { Text(stringResource(filter.labelRes)) },
                )
            }
        }
        Text(
            text = stringResource(R.string.recent_activity_filter_status_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = selectedStatusFilters.isEmpty(),
                onClick = { onStatusFiltersChange(emptySet()) },
                label = { Text(stringResource(R.string.recent_activity_filter_status_all)) },
            )
            EventStatusFilter.entries.forEach { filter ->
                FilterChip(
                    selected = filter in selectedStatusFilters,
                    onClick = {
                        onStatusFiltersChange(selectedStatusFilters.toggle(filter))
                    },
                    label = { Text(stringResource(filter.labelRes)) },
                )
            }
        }
    }
}

private enum class EventTypeFilter(val labelRes: Int) {
    Notification(R.string.recent_activity_filter_type_notification),
    PassThrough(R.string.recent_activity_filter_type_pass_through),
    Registration(R.string.recent_activity_filter_type_registration),
    Other(R.string.recent_activity_filter_type_other),
}

private enum class EventStatusFilter(val labelRes: Int) {
    Normal(R.string.recent_activity_filter_status_normal),
    Disabled(R.string.recent_activity_filter_status_disabled),
    Denied(R.string.recent_activity_filter_status_denied),
}

private fun EventInfoForDisplay.matchesFilters(
    selectedTypeFilters: Set<EventTypeFilter>,
    selectedStatusFilters: Set<EventStatusFilter>,
): Boolean {
    val matchesType = selectedTypeFilters.isEmpty() || selectedTypeFilters.any { filter ->
        when (filter) {
            EventTypeFilter.Notification -> event.canReplayNotification() && !isPassThroughMessage()
            EventTypeFilter.PassThrough -> event.type == ManagerEventType.SEND_MESSAGE && isPassThroughMessage()
            EventTypeFilter.Registration -> event.type in setOf(
                ManagerEventType.REGISTRATION,
                ManagerEventType.REGISTRATION_RESULT,
                ManagerEventType.UN_REGISTRATION,
            )
            EventTypeFilter.Other -> event.type !in setOf(
                ManagerEventType.SEND_MESSAGE,
                ManagerEventType.NOTIFICATION,
                ManagerEventType.REGISTRATION,
                ManagerEventType.REGISTRATION_RESULT,
                ManagerEventType.UN_REGISTRATION,
            )
        }
    }
    val matchesStatus = selectedStatusFilters.isEmpty() || selectedStatusFilters.any { filter ->
        when (filter) {
            EventStatusFilter.Normal -> !isDisabled() && event.result == ManagerEventResult.OK
            EventStatusFilter.Disabled -> isDisabled()
            EventStatusFilter.Denied -> event.result != ManagerEventResult.OK
        }
    }
    return matchesType && matchesStatus
}

private fun <T> Set<T>.toggle(value: T): Set<T> {
    return if (contains(value)) this - value else this + value
}

private fun ManagerEvent.canReplayNotification(): Boolean {
    return type == ManagerEventType.SEND_MESSAGE || type == ManagerEventType.NOTIFICATION
}

private fun EventInfoForDisplay.isPassThroughMessage(): Boolean {
    return channel == Utils.getApplication()?.getString(R.string.message_type_pass_through)
}

private fun EventInfoForDisplay.isDisabled(): Boolean = configOptions.contains("disable")

@Composable
private fun EventGroupList(
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
                    val sortedEvents = events.sortedByDescending { it.receiveDate.time }
                    val first = sortedEvents.first()
                    EventGroupForDisplay(
                        packageName = first.packageName,
                        appName = first.appName?.takeIf { it.isNotBlank() } ?: first.packageName,
                        events = sortedEvents,
                        latestDate = first.receiveDate,
                    )
                }
                .sortedByDescending { it.latestDate.time }
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
        val listKey = viewModel.cacheKey(query, "", refreshSignal)
        if (restoredListKey == listKey) return@LaunchedEffect
        withFrameNanos { }
        // Cache-first: serve from the persistent store (or in-memory snapshot)
        // immediately so the page paints without a 3s+ remote round-trip.
        // We do NOT proactively hit the runtime here; remote is only used on
        // a genuine cache miss, user pull-to-refresh, or background silent refresh.
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
            Log.w("ManagerRuntime", "EventList loadNextPage unavailable status=${error.status}")
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
                                .setData(Uri.parse(group.packageName)),
                        )
                    },
                    leadingContent = {
                        AppIconImage(group.packageName, group.appName, modifier = Modifier.size(48.dp))
                    },
                    trailingContent = {
                        Icon(
                            painter = painterResource(CommonR.drawable.ic_keyboard_arrow_right_black_24dp),
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
                            text = stringResource(R.string.recent_activity_group_count, group.events.size),
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

@Composable
private fun EventDetailsDialog(
    clickedEvent: EventInfoForDisplay,
    content: String? = null,
    viewModel: EventListViewModel,
    onDismiss: () -> Unit
) {
    var json by remember(clickedEvent.id, content) {
        mutableStateOf(
            content ?: buildEventDebugInfo(clickedEvent)
        )
    }
    LaunchedEffect(clickedEvent.id, content) {
        if (content == null) {
            json = viewModel.getJson(clickedEvent.event) ?: buildEventDebugInfo(clickedEvent)
        }
    }
    var softWrap by rememberSaveable { mutableStateOf(false) }
    val context = LocalContext.current
    val replayScope = rememberCoroutineScope()
    val canReplayNotification = clickedEvent.event.canReplayNotification()
    val verticalScroll = rememberScrollState()
    val horizontalScroll = rememberScrollState()

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val targetHeight = screenHeight * 0.9f
    val bodyMaxHeight = screenHeight * 0.62f

    AlertDialog(
        onDismiss,
        {
            DialogActionRow(
                actions = buildList {
                    add(
                        DialogAction(
                            label = stringResource(android.R.string.copy),
                            onClick = { viewModel.copyToClipboard(json) }
                        )
                    )
                    if (canReplayNotification) {
                        add(
                            DialogAction(
                                label = stringResource(R.string.action_notify),
                                onClick = {
                                    replayScope.launch {
                                        val outcome = viewModel.mockMessage(clickedEvent.event)
                                        Napier.d(
                                            "Replay event id=${clickedEvent.id} pkg=${clickedEvent.packageName} outcome=$outcome",
                                            tag = "EventListPage",
                                        )
                                        Utils.makeText(
                                            context,
                                            context.getString(outcome.feedbackStringRes()),
                                            0,
                                        )
                                    }
                                }
                            )
                        )
                    }
                }
            )
        },
        title = {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(36.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.event_detail_developer_info),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { softWrap = !softWrap }) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.WrapText,
                        contentDescription = stringResource(R.string.event_detail_soft_wrap_cd),
                        tint = if (softWrap) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(
                            if (softWrap) {
                                R.string.event_detail_soft_wrap_on
                            } else {
                                R.string.event_detail_soft_wrap_off
                            }
                        )
                    )
                }
                IconButton(onClick = { viewModel.startManagePermissions(clickedEvent.packageName) }) {
                    Icon(
                        painter = painterResource(id = CommonR.drawable.ic_info_outline_black_24dp),
                        contentDescription = stringResource(R.string.action_app_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            SelectionContainer {
                val textModifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = bodyMaxHeight)
                    .verticalScroll(verticalScroll)
                    .then(
                        if (softWrap) {
                            Modifier
                        } else {
                            Modifier.horizontalScroll(horizontalScroll)
                        }
                    )
                Text(
                    text = json,
                    modifier = textModifier,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    softWrap = softWrap,
                )
            }
        },
        modifier = Modifier.heightIn(Dp.Unspecified, targetHeight)
    )
}

internal fun MockReplayOutcome.feedbackStringRes(): Int = when (this) {
    MockReplayOutcome.BlockedByPermission -> R.string.mock_notification_blocked_by_permission
    MockReplayOutcome.Dispatched -> R.string.mock_notification_dispatched
    MockReplayOutcome.Posted -> R.string.mock_notification_posted
    MockReplayOutcome.Failed -> R.string.mock_notification_failed
}

private fun buildEventDebugInfo(event: EventInfoForDisplay): String {
    return runCatching { EventDebugJson.format(event.event) }.getOrElse {
        buildString {
            appendLine("packageName=${event.packageName}")
            appendLine("appName=${event.appName ?: "<unknown>"}")
            appendLine("title=${event.title}")
            appendLine("channel=${event.channel.ifBlank { "<none>" }}")
            appendLine("configOptions=${event.configOptions.joinToString(",").ifBlank { "<none>" }}")
            appendLine("receiveDate=${Instant.ofEpochMilli(event.receiveDate.time).atZone(ZoneId.systemDefault()).format(receiveDateTimeFormatter)}")
            appendLine("type=${event.event.type}")
            appendLine("result=${event.event.result}")
            appendLine("info=${event.event.info ?: "<none>"}")
            appendLine("payloadBytes=${event.event.payload?.size ?: 0}")
            appendLine("content=${event.content}")
        }
    }
}

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
private fun EventList(
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
                Log.w("ManagerRuntime", "EventList doLoadMore unavailable status=${error.status}")
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
                Log.w("ManagerRuntime", "EventList doRefresh unavailable status=${error.status}")
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
private fun DeleteCountdownSnackbar(data: androidx.compose.material3.SnackbarData) {
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

@Composable
fun EmptyEventState(modifier: Modifier = Modifier) {
    WorkspaceEmptyState(
        title = stringResource(R.string.event_empty_title),
        summary = stringResource(R.string.event_empty_summary),
        modifier = modifier,
        icon = {
            Icon(
                painter = painterResource(id = CommonR.drawable.ic_event_note_black_24dp),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
            )
        },
    )
}

@Composable
private fun InitialEventLoadState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .heightIn(min = 300.dp)
            .padding(horizontal = 32.dp, vertical = 72.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = stringResource(R.string.event_initial_load_title),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        Text(
            text = stringResource(R.string.event_initial_load_summary),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun EventLoadFailedState(modifier: Modifier = Modifier) {
    WorkspaceEmptyState(
        title = stringResource(R.string.event_load_failed_title),
        summary = stringResource(R.string.event_load_failed_summary),
        modifier = modifier.heightIn(min = 300.dp),
        icon = {
            Icon(
                painter = painterResource(id = CommonR.drawable.ic_event_note_black_24dp),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            )
        },
    )
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
)
@Composable
fun EventDetailsDialogPreview() {
    // Cannot easily preview with ViewModel dependency
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
    showSystemUi = true,
)
@Composable
fun EventListPreview() {
    // Napier FileAntilog is owned by ManagerRuntimeFileLog.init
    Utils.context = LocalContext.current

    val eventListSequence = sequence {
        yield(
            listOf(
                EventInfoForDisplay(
                    1212, "123",
                    setOf("123", "456"), "c1", date(2025, 1, 1),
                    "title", "content"
                ),
                EventInfoForDisplay(
                    123, "123",
                    setOf("disable", "456"), "c1", date(2025, 1, 1),
                    "title", "content"
                )
            )
        )
        yield((0..10).map {
            val str = it.toString().repeat(3)
            EventInfoForDisplay(
                it.toLong(), str,
                setOf(str), str, date(2025, 1, 1),
                str, str
            )
        })
        yield((11..20).map {
            val str = it.toString().repeat(3)
            EventInfoForDisplay(
                it.toLong(), str,
                setOf(str), str, date(2025, 1, 1),
                str, str
            )
        })
        yield((21..30).map {
            val str = it.toString().repeat(3)
            EventInfoForDisplay(
                it.toLong(), str,
                setOf(str), str, date(2025, 1, 1),
                str, str
            )
        })
    }.iterator()

    val getEvents = { isRefresh: Boolean ->
        if (eventListSequence.hasNext()) eventListSequence.next() else emptyList()
    }

    Page {
        EventList(
            onClick = { },
            getEvents = getEvents,
            query = "",
            packageName = "",
            contentPadding = PaddingValues(0.dp),
            snackbarHostState = remember { SnackbarHostState() },
            viewModel = koinViewModel(),
        )
    }
}

private fun date(year: Int, month: Int, day: Int): Date {
    return Calendar.getInstance().apply {
        set(year, month - 1, day, 0, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.time
}


data class EventInfoForDisplay(
    val id: Long,
    val packageName: String,
    val configOptions: Set<String>,
    val channel: String,
    val receiveDate: Date,
    val title: String,
    val content: String,
    val appName: String? = null,
    val event: ManagerEvent = ManagerEvent(
        id = id,
        packageName = packageName,
        configOptions = configOptions,
        channel = channel,
        receiveDateMs = receiveDate.time,
        title = title,
        content = content,
        appName = appName,
    ),
)
