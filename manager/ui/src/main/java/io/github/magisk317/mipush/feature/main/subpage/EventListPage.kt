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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
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

internal val receiveDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

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
        var preferenceRefreshSignal by rememberSaveable { mutableIntStateOf(0) }
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
                    summary = pluralStringResource(
                        R.plurals.event_retention_summary,
                        eventRetentionDays,
                        eventRetentionDays,
                    ),
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
                val resources = LocalResources.current
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
                                    resources.getQuantityString(
                                        R.plurals.event_cleanup_done,
                                        deleted,
                                        deleted,
                                    )
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
internal fun InitialEventLoadState(modifier: Modifier = Modifier) {
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
internal fun EventLoadFailedState(modifier: Modifier = Modifier) {
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


@Immutable
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
