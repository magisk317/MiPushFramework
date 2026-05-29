@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.magisk317.mipush.feature.main.subpage

import android.content.Intent
import android.net.Uri
import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
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
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.ManagerEventResult
import io.github.magisk317.mipush.common.manager.ManagerEventType
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.feature.ui.component.AppIcon
import io.github.magisk317.mipush.feature.ui.component.DialogAction
import io.github.magisk317.mipush.feature.ui.component.DialogActionRow
import io.github.magisk317.mipush.feature.ui.component.InfoPill
import io.github.magisk317.mipush.feature.ui.component.OverlayHeaderScaffold
import io.github.magisk317.mipush.feature.ui.component.RefreshableLazyColumn
import io.github.magisk317.mipush.feature.ui.component.ScrollToTopFAB
import io.github.magisk317.mipush.feature.ui.component.SearchBar
import io.github.magisk317.mipush.feature.ui.component.TextView
import io.github.magisk317.mipush.feature.ui.component.WorkspaceEmptyState
import io.github.magisk317.mipush.feature.ui.component.WorkspaceListItem
import io.github.magisk317.mipush.platform.support.LegacyUiEntryPoints
import io.github.magisk317.mipush.feature.main.MainScrollChromeState
import io.github.magisk317.mipush.feature.ui.theme.spacing
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date

import io.github.magisk317.mipush.main.viewmodel.EventListViewModel
import org.koin.compose.viewmodel.koinViewModel

private val receiveDateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

@Composable
fun EventList(
    query: String = "",
    packageName: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    groupByApp: Boolean = false,
    viewModel: EventListViewModel = koinViewModel(),
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
    scrollChromeState: MainScrollChromeState? = null,
) {
    Page {
        Box(modifier = Modifier.fillMaxSize()) {
            val context = LocalContext.current
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        var clickedEvent by remember { mutableStateOf<EventInfoForDisplay?>(null) }
        var currentQuery by rememberSaveable(query) { mutableStateOf(query) }
        var searchExpanded by rememberSaveable(query) { mutableStateOf(query.isNotBlank()) }
        var selectedTypeFilters by remember { mutableStateOf(emptySet<EventTypeFilter>()) }
        var selectedStatusFilters by remember { mutableStateOf(emptySet<EventStatusFilter>()) }
        var groupMode by rememberSaveable(groupByApp, packageName) { mutableStateOf(groupByApp) }
        val showGroupedByApp = packageName.isEmpty() && groupMode
        val snackbarHostState = remember { SnackbarHostState() }
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
            headerVisible = scrollChromeState?.isChromeVisible ?: true,
            overlayModifier = Modifier
                .fillMaxWidth()
                .then(
                    if (hazeState != null && hazeStyle != null) {
                        Modifier.hazeEffect(hazeState, hazeStyle) {
                            forceInvalidateOnPreDraw = true
                        }
                    } else {
                        Modifier
                    }
                ),
            content = { listPadding ->
                if (showGroupedByApp) {
                    EventGroupList(
                        query = currentQuery,
                        refreshSignal = refreshSignal,
                        contentPadding = PaddingValues(
                            top = listPadding.calculateTopPadding() + 8.dp,
                            bottom = listPadding.calculateBottomPadding(),
                        ),
                        viewModel = viewModel,
                        selectedTypeFilters = selectedTypeFilters,
                        selectedStatusFilters = selectedStatusFilters,
                        hazeState = hazeState,
                        scrollChromeState = scrollChromeState,
                        listState = listState,
                    )
                } else {
                    var lastId by rememberSaveable(refreshSignal) { mutableStateOf<Long?>(null) }
                    EventList(
                        onClick = { clickedEvent = it },
                        getEvents = { isRefresh ->
                            if (isRefresh) lastId = null
                            viewModel.fetchEventsSuspend(isRefresh, lastId, packageName, currentQuery).also { list ->
                                list.lastOrNull()?.let { lastId = it.id }
                            }
                        },
                        query = currentQuery,
                        packageName = packageName,
                        refreshSignal = refreshSignal,
                        contentPadding = PaddingValues(
                            top = listPadding.calculateTopPadding() + 8.dp,
                            bottom = listPadding.calculateBottomPadding(),
                        ),
                        selectedTypeFilters = selectedTypeFilters,
                        selectedStatusFilters = selectedStatusFilters,
                        hazeState = hazeState,
                        hazeStyle = hazeStyle,
                        snackbarHostState = snackbarHostState,
                        viewModel = viewModel,
                        scrollChromeState = scrollChromeState,
                        listState = listState,
                    )
                }
            },
            overlay = {
                TopAppBar(
                    title = {
                        Text(
                            if (packageName.isNotEmpty()) resolvedTitle ?: packageName else stringResource(R.string.recent_activity_title),
                        )
                    },
                    windowInsets = WindowInsets.statusBars,
                    actions = {
                        if (packageName.isEmpty()) {
                            IconButton(onClick = { groupMode = !groupMode }) {
                                Icon(
                                    painter = painterResource(
                                        if (showGroupedByApp) {
                                            R.drawable.ic_event_note_black_24dp
                                        } else {
                                            R.drawable.ic_apps_black_24dp
                                        },
                                    ),
                                    contentDescription = if (showGroupedByApp) {
                                        stringResource(R.string.recent_activity_action_show_events)
                                    } else {
                                        stringResource(R.string.recent_activity_action_group_by_app)
                                    },
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        IconButton(onClick = { searchExpanded = !searchExpanded }) {
                            Icon(
                                painter = painterResource(R.drawable.ic_search_24dp),
                                contentDescription = stringResource(R.string.action_search),
                                tint = if (searchExpanded || currentQuery.isNotBlank()) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = Color.Transparent,
                        scrolledContainerColor = Color.Transparent,
                    ),
                )
                if (searchExpanded || currentQuery.isNotBlank()) {
                    SearchBar(
                        placeholder = stringResource(android.R.string.search_go),
                        query = currentQuery,
                        onValueChange = { currentQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = MaterialTheme.spacing.medium),
                    )
                }
            }
        )
            val snackbarBottomPadding = contentPadding.calculateBottomPadding() +
                WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding() +
                MaterialTheme.spacing.medium
            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(
                        start = MaterialTheme.spacing.medium,
                        end = MaterialTheme.spacing.medium,
                        bottom = snackbarBottomPadding,
                    ),
            ) { data ->
                val dismissState = rememberSwipeToDismissBoxState()
                LaunchedEffect(dismissState.currentValue, data) {
                    if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
                        data.dismiss()
                    }
                }
                SwipeToDismissBox(
                    state = dismissState,
                    enableDismissFromStartToEnd = true,
                    enableDismissFromEndToStart = true,
                    backgroundContent = {},
                ) {
                    DeleteCountdownSnackbar(data)
                }
            }
            ScrollToTopFAB(
                listState = listState,
                visible = snackbarHostState.currentSnackbarData == null,
            )
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
            EventTypeFilter.Notification -> event.type == ManagerEventType.SEND_MESSAGE && !isPassThroughMessage()
            EventTypeFilter.PassThrough -> event.type == ManagerEventType.SEND_MESSAGE && isPassThroughMessage()
            EventTypeFilter.Registration -> event.type in setOf(
                ManagerEventType.REGISTRATION,
                ManagerEventType.REGISTRATION_RESULT,
                ManagerEventType.UN_REGISTRATION,
            )
            EventTypeFilter.Other -> event.type !in setOf(
                ManagerEventType.SEND_MESSAGE,
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

private fun EventInfoForDisplay.isPassThroughMessage(): Boolean {
    return channel == Utils.getApplication()?.getString(R.string.message_type_pass_through)
}

private fun EventInfoForDisplay.isDisabled(): Boolean = configOptions.contains("disable")

@Composable
private fun EventGroupList(
    query: String,
    refreshSignal: Int,
    contentPadding: PaddingValues,
    viewModel: EventListViewModel,
    selectedTypeFilters: Set<EventTypeFilter>,
    selectedStatusFilters: Set<EventStatusFilter>,
    hazeState: HazeState? = null,
    scrollChromeState: MainScrollChromeState? = null,
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
) {
    val context = LocalContext.current
    val groupedItems = remember(query) { mutableStateListOf<EventGroupForDisplay>() }
    val allEvents = remember(query) { mutableStateListOf<EventInfoForDisplay>() }
    var lastId by rememberSaveable(query, refreshSignal) { mutableStateOf<Long?>(null) }
    var hasMore by rememberSaveable(query) { mutableStateOf(true) }
    var isNeedRefresh by rememberSaveable(query, refreshSignal) { mutableStateOf(true) }
    var isLoading by remember { mutableStateOf(false) }
    fun rebuildGroups() {
        val grouped = allEvents
            .filter { it.matchesFilters(selectedTypeFilters, selectedStatusFilters) }
            .groupBy { it.packageName }
            .map { (pkg, events) ->
                val sortedEvents = events.sortedByDescending { it.receiveDate.time }
                val first = sortedEvents.first()
                EventGroupForDisplay(
                    packageName = pkg,
                    appName = first.appName?.takeIf { it.isNotBlank() } ?: pkg,
                    events = sortedEvents,
                    latestDate = first.receiveDate
                )
            }
            .sortedByDescending { it.latestDate.time }
        groupedItems.clear()
        groupedItems.addAll(grouped)
    }

    androidx.compose.runtime.LaunchedEffect(selectedTypeFilters, selectedStatusFilters) {
        rebuildGroups()
    }

    suspend fun loadNextPage(isRefresh: Boolean) {
        if (isLoading) return
        isLoading = true
        try {
            val events = viewModel.fetchEventsSuspend(isRefresh, lastId, "", query)
            
            if (isRefresh) {
                allEvents.clear()
            }
            allEvents.addAll(events)
            events.lastOrNull()?.let { lastId = it.id }
            hasMore = events.size >= Constants.PAGE_SIZE
            rebuildGroups()
        } finally {
            isLoading = false
        }
    }

    val refreshScope = rememberCoroutineScope { Dispatchers.IO }
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
        scrollChromeState = scrollChromeState,
        contentPadding = contentPadding,
        modifier = if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier,
        listState = listState,
    ) {
        if (groupedItems.isEmpty() && !isLoading) {
            item {
                EmptyEventState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                )
            }
        } else {
            items(groupedItems, key = { it.packageName }) { group ->
                val updatedAt = friendlyDateString(
                    group.latestDate,
                    Utils.getUTC(),
                    context
                )
                WorkspaceListItem(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp),
                    containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f),
                    onClick = {
                        context.startActivity(
                            LegacyUiEntryPoints.recentEventListIntent(
                                context = context,
                                packageName = group.packageName,
                            ),
                        )
                    },
                    leadingContent = {
                        AppIcon(group.packageName, group.appName, modifier = Modifier.size(48.dp))
                    },
                    trailingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_keyboard_arrow_right_black_24dp),
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
    var json by remember {
        mutableStateOf(
            content
                ?: viewModel.getJson(clickedEvent.event)
                ?: buildEventDebugInfo(clickedEvent)
        )
    }
    val context = LocalContext.current

    val screenHeight = LocalConfiguration.current.screenHeightDp.dp
    val targetHeight = screenHeight * 0.9f

    AlertDialog(
        onDismiss,
        {
            DialogActionRow(
                actions = listOf(
                    DialogAction(
                        label = stringResource(android.R.string.copy),
                        onClick = { viewModel.copyToClipboard(json) }
                    ),
                    DialogAction(
                        label = stringResource(R.string.action_notify),
                        onClick = {
                            if (viewModel.mockMessage(clickedEvent.event)) {
                                Unit
                            } else {
                                Napier.w(
                                    "Cannot replay event id=${clickedEvent.id} pkg=${clickedEvent.packageName}: container unavailable",
                                    tag = "EventListPage",
                                )
                                Utils.makeText(
                                    context,
                                    context.getString(R.string.mock_notification_failed),
                                    0,
                                )
                            }
                        }
                    ),
                )
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
                    style = MaterialTheme.typography.titleLarge
                )
                IconButton(onClick = { viewModel.startManagePermissions(clickedEvent.packageName) }) {
                    Icon(
                        painter = painterResource(id = R.drawable.ic_info_outline_black_24dp),
                        contentDescription = stringResource(R.string.action_app_info),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            TextView(json)
        },
        modifier = Modifier.heightIn(Dp.Unspecified, targetHeight)
    )
}

private fun buildEventDebugInfo(event: EventInfoForDisplay): String {
    return buildString {
        appendLine("packageName=${event.packageName}")
        appendLine("appName=${event.appName ?: "<unknown>"}")
        appendLine("title=${event.title}")
        appendLine("channel=${event.channel.ifBlank { "<none>" }}")
        appendLine("configOptions=${event.configOptions.joinToString(",").ifBlank { "<none>" }}")
        appendLine("receiveDate=${receiveDateFormat.format(event.receiveDate)}")
        appendLine("type=${event.event.type}")
        appendLine("result=${event.event.result}")
        appendLine("info=${event.event.info ?: "<none>"}")
        appendLine("payloadBytes=${event.event.payload?.size ?: 0}")
        appendLine("content=${event.content}")
    }
}

private val g_items = mutableStateListOf<EventInfoForDisplay>()

private fun EventInfoForDisplay.composeKey(): String {
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
    contentPadding: PaddingValues = PaddingValues(0.dp),
    selectedTypeFilters: Set<EventTypeFilter> = emptySet(),
    selectedStatusFilters: Set<EventStatusFilter> = emptySet(),
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
    snackbarHostState: SnackbarHostState,
    viewModel: EventListViewModel,
    scrollChromeState: MainScrollChromeState? = null,
    listState: androidx.compose.foundation.lazy.LazyListState? = null,
) {
    val isPreview = LocalInspectionMode.current
    val context = LocalContext.current
    val recentActivityDeletedMessage = stringResource(R.string.recent_activity_deleted)
    val actionUndoLabel = stringResource(R.string.action_undo)
    val items = remember {
        if (packageName.isEmpty() && !isPreview) g_items
        else mutableStateListOf()
    }

    val refreshScope = rememberCoroutineScope { Dispatchers.IO }
    val actionScope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var hasMore by rememberSaveable(query, packageName) { mutableStateOf(true) }
    val doLoadMore: (onRefreshed: () -> Unit) -> Unit = doLoadMore@{ onRefreshed ->
        if (isLoading || !hasMore) {
            onRefreshed()
            return@doLoadMore
        }
        isLoading = true
        refreshScope.launch {
            val loaded = getEvents(items.isEmpty())
            withContext(Dispatchers.Main) {
                items.appendDistinct(loaded)
                hasMore = loaded.size >= Constants.PAGE_SIZE
                isLoading = false
                onRefreshed()
            }
        }
    }
    val shouldRefresh = items.isEmpty() || query.isNotEmpty() || packageName.isNotEmpty() || refreshSignal > 0
    var isNeedRefresh by rememberSaveable(query, packageName, refreshSignal) { mutableStateOf(shouldRefresh) }
    val doRefresh: (onRefreshed: () -> Unit) -> Unit = doRefresh@{ onRefreshed ->
        if (isLoading) {
            onRefreshed()
            return@doRefresh
        }
        isLoading = true
        refreshScope.launch {
            val elements = getEvents(true)
            withContext(Dispatchers.Main) {
                items.clear()
                items.appendDistinct(elements)
                hasMore = elements.size >= Constants.PAGE_SIZE
                isLoading = false
                isNeedRefresh = false
                onRefreshed()
            }
        }
    }

    val isNeedMore: (Int) -> Boolean = { hasMore && !isLoading && it >= items.size - 10 }
    val filteredItems = items.filter { it.matchesFilters(selectedTypeFilters, selectedStatusFilters) }

    fun deleteEventWithUndo(item: EventInfoForDisplay) {
        val key = item.composeKey()
        val insertAt = items.indexOfFirst { it.composeKey() == key }.coerceAtLeast(0)
        items.removeAll { it.composeKey() == key }

        actionScope.launch {
            viewModel.deleteEvent(item)
            snackbarHostState.currentSnackbarData?.dismiss()
            val result = snackbarHostState.showSnackbar(
                message = recentActivityDeletedMessage,
                actionLabel = actionUndoLabel,
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.restoreEvent(item)?.let { restored ->
                    val idx = insertAt.coerceIn(0, items.size)
                    items.add(idx, restored)
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
        scrollChromeState = scrollChromeState,
        contentPadding = contentPadding,
        modifier = if (hazeState != null) {
            Modifier.hazeSource(hazeState)
        } else {
            Modifier
        },
        listState = listState,
    ) {
        if (filteredItems.isEmpty() && !isLoading) {
            item {
                EmptyEventState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                )
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
    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue == SwipeToDismissBoxValue.Settled) return@LaunchedEffect
        onDelete(item)
        dismissState.reset()
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
    val titleText = if (disabled) "[disable] ${item.title}" else item.title
    val metaLine = if (item.channel.isNotBlank()) {
        "$appName · ${item.channel}"
    } else {
        appName
    }
    val surface = MaterialTheme.colorScheme.surface
    val containerColor = when {
        disabled -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.22f).compositeOver(surface)
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
            AppIcon(item.packageName, item.appName, modifier = Modifier.size(40.dp))
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
                text = receiveDateFormat.format(item.receiveDate),
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
            if (denied) {
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
    LaunchedEffect(data) {
        repeat(5) {
            delay(1_000L)
            secondsLeft--
        }
        data.dismiss()
    }
    Snackbar(
        action = {
            TextButton(onClick = { data.performAction() }) {
                Text("${data.visuals.actionLabel} (${secondsLeft}s)")
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
                painter = painterResource(id = R.drawable.ic_event_note_black_24dp),
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
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
    Napier.base(io.github.aakira.napier.DebugAntilog())
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
