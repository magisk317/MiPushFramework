@file:android.annotation.SuppressLint("LocalContextGetResourceValueCall")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package io.github.magisk317.mipush.feature.main.subpage


import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.EventNote
import androidx.compose.material3.Icon
import io.github.magisk317.uikit.surface.AppLinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.common.AppSnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.R
import kotlinx.coroutines.launch
import io.github.magisk317.mipush.common.cache.ApplicationNameCache
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.uikit.surface.InfoPill
import io.github.magisk317.uikit.surface.rememberSearchOverlayState
import io.github.magisk317.uikit.surface.SearchOverlayState
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.uikit.surface.AppBottomSheet
import io.github.magisk317.uikit.preference.StateSwitchItem
import io.github.magisk317.uikit.preference.Item as SettingsItem
import io.github.magisk317.uikit.preference.TextInputDialog
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Date

import io.github.magisk317.mipush.main.viewmodel.EventListViewModel
import io.github.magisk317.mipush.main.viewmodel.SettingsViewModel
import org.koin.compose.viewmodel.koinViewModel
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.text.style.TextAlign
import io.github.magisk317.uikit.surface.WorkspaceEmptyState

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
    val context = LocalContext.current
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        var clickedEvent by remember { mutableStateOf<EventInfoForDisplay?>(null) }
        var currentQuery by rememberSaveable(query) { mutableStateOf(query) }
        var preferenceRefreshSignal by rememberSaveable { mutableIntStateOf(0) }
        var searchRefreshSignal by rememberSaveable { mutableIntStateOf(0) }
        val effectiveRefreshSignal = refreshSignal + preferenceRefreshSignal + searchRefreshSignal
        val searchState = rememberSearchOverlayState(
            initialQuery = query,
            onSearchChange = {
                currentQuery = it
                searchRefreshSignal++
            },
        )
        var selectedTypeFilters by remember { mutableStateOf(emptySet<EventTypeFilter>()) }
        var selectedStatusFilters by remember { mutableStateOf(emptySet<EventStatusFilter>()) }
        var groupMode by rememberSaveable(groupByApp, packageName) { mutableStateOf(groupByApp) }
        val showGroupedByApp = packageName.isEmpty() && groupMode
        val snackbarHostState = remember { AppSnackbarHostState() }
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

        BackHandler(enabled = searchState.expanded) {
            searchState.close()
        }

        LaunchedEffect(isActive) {
            if (!isActive) {
                searchState.close()
            }
        }

        clickedEvent?.let {
            EventDetailsDialog(it, viewModel = viewModel) { clickedEvent = null }
        }

            val body: @Composable (PaddingValues) -> Unit = { listPadding ->
                key(currentQuery, packageName, effectiveRefreshSignal, showGroupedByApp) {
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
                }
            }
            val state = EventListUiState(
                searchState = searchState,
                heroTitle = if (packageName.isNotEmpty()) {
                    resolvedTitle ?: packageName
                } else {
                    stringResource(R.string.recent_activity_title)
                },
                showSettings = packageName.isEmpty(),
            )
            val actions = EventListActions(
                onSettingsClick = { showListSettingsSheet = true },
                onCloseSearch = { searchState.close() },
            )
            when (currentUiKitStyle()) {
                UiKitStyle.Miuix -> EventListMiuix(
                    state = state,
                    actions = actions,
                    snackbarHostState = snackbarHostState,
                    listState = listState,
                    scrollScope = scope,
                    scrollChromeState = scrollChromeState,
                    contentBottomPadding = contentPadding.calculateBottomPadding(),
                    body = body,
                )

                UiKitStyle.Expressive -> EventListExpressive(
                    state = state,
                    actions = actions,
                    snackbarHostState = snackbarHostState,
                    listState = listState,
                    scrollScope = scope,
                    scrollChromeState = scrollChromeState,
                    contentBottomPadding = contentPadding.calculateBottomPadding(),
                    body = body,
                )
            }

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
                imageVector = Icons.AutoMirrored.Filled.EventNote,
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
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        AppLinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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
                imageVector = Icons.AutoMirrored.Filled.EventNote,
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
            snackbarHostState = remember { AppSnackbarHostState() },
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


/**
 * Style-agnostic render state for the recent-activity tab (KernelSU
 * `SuperUserUiState` model).
 */
internal data class EventListUiState(
    val searchState: SearchOverlayState,
    val heroTitle: String,
    val showSettings: Boolean,
)

/** Action callbacks for the recent-activity tab (KernelSU `SuperUserActions` model). */
internal class EventListActions(
    val onSettingsClick: () -> Unit,
    val onCloseSearch: () -> Unit,
)
