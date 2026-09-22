package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.uikit.surface.AppBadge

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import io.github.magisk317.uikit.surface.AppHorizontalDivider
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import io.github.magisk317.uikit.surface.AppIconButton
import androidx.compose.material3.MaterialTheme
import io.github.magisk317.uikit.surface.AppTopBar
import io.github.magisk317.uikit.surface.AppTextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
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
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.main.viewmodel.ApplicationListViewModel
import io.github.magisk317.mipush.manager.application.ApplicationReadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import io.github.magisk317.mipush.manager.application.ManagerApplication
import androidx.compose.material3.Text
import androidx.compose.ui.res.stringResource
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.mipush.feature.main.RegistrationStateStyle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.mipush.feature.ui.component.RefreshableLazyColumn
import io.github.magisk317.uikit.surface.InfoPill
import io.github.magisk317.uikit.theme.spacing
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.surface.rememberSearchOverlayState
import io.github.magisk317.uikit.surface.SearchOverlayState
import io.github.magisk317.uikit.theme.UiKitStyle
import io.github.magisk317.uikit.theme.currentUiKitStyle
import io.github.magisk317.uikit.surface.AppBottomSheet
import io.github.magisk317.uikit.preference.StateSwitchItem
import org.koin.compose.viewmodel.koinViewModel

@Immutable
data class AppInfoForDisplay(
    val registrationState: Pair<Int, Color>,
    val lastReceiveTime: String,
    val isZygiskEnabled: Boolean? = null,
)

private val TAG = "ApplicationListPage"
@Composable
fun ApplicationList(
    query: String,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    filterMode: Int = 0,
    isActive: Boolean = true,
    onAppClick: (String) -> Unit,
    scrollChromeState: ScrollChromeState? = null,
) {
    val listViewModel: ApplicationListViewModel = koinViewModel()
    val items by listViewModel.items.collectAsState()
    val itemsInfo by listViewModel.itemsInfo.collectAsState()
    val stats by listViewModel.stats.collectAsState()
    val unavailableStatus by listViewModel.unavailableStatus.collectAsState()
    val showSystemApps by listViewModel.showSystemApps.collectAsState()
    val context = LocalContext.current
    var showListSettingsSheet by rememberSaveable { mutableStateOf(false) }

    var currentQuery by rememberSaveable(query) { mutableStateOf(query) }
    val searchState = rememberSearchOverlayState(
        initialQuery = query,
        onSearchChange = { currentQuery = it },
    )

    // Auto-load only when cache miss (first enter / search / filter / external refreshSignal).
    // Tab re-enter with same query+filter and non-empty VM cache skips IO; pull-to-refresh always loads.
    var isNeedRefresh by remember { mutableStateOf(false) }
    var handledRefreshSignal by rememberSaveable { mutableIntStateOf(0) }
    var lifecycleResumeSignal by remember { mutableIntStateOf(0) }
    var wasActive by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, isActive) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && isActive) {
                lifecycleResumeSignal += 1
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // showSystemApps toggles reload inside ViewModel.setShowSystemApps to avoid double IO.
    // currentQuery in keys keeps search live; cache hit short-circuits.
    LaunchedEffect(isActive, currentQuery, refreshSignal, filterMode, lifecycleResumeSignal) {
        if (!isActive && currentQuery.isBlank()) {
            wasActive = false
            return@LaunchedEffect
        }
        withFrameNanos { }
        val enteredPage = !wasActive
        wasActive = true
        val forceBySignal = refreshSignal > handledRefreshSignal
        val forceByLifecycle = lifecycleResumeSignal > 0
        if (!enteredPage && !forceBySignal && !forceByLifecycle &&
            listViewModel.hasCachedList(currentQuery, filterMode)
        ) {
            isNeedRefresh = false
            return@LaunchedEffect
        }
        listViewModel.loadApplications(currentQuery, filterMode) {
            handledRefreshSignal = refreshSignal
            isNeedRefresh = false
        }
    }

    val refreshScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    DisposableEffect(Unit) {
        onDispose {
            refreshScope.cancel()
        }
    }

    val onRefresh: (onRefreshed: () -> Unit) -> Unit = { onRefreshed ->
        listViewModel.loadApplications(currentQuery, filterMode) {
            handledRefreshSignal = refreshSignal
            isNeedRefresh = false
            onRefreshed()
        }
    }

    fun closeSearch() {
        searchState.close()
        // The query reset must also replace the ViewModel snapshot. Relying
        // only on the LaunchedEffect below can leave the filtered snapshot
        // visible when a query-scoped cache is already considered loaded.
        listViewModel.loadApplications(
            query = "",
            filterMode = filterMode,
            includeSystemApps = showSystemApps,
        )
    }

    BackHandler(enabled = searchState.expanded) {
        closeSearch()
    }

    LaunchedEffect(isActive) {
        if (!isActive) {
            searchState.close()
        }
    }

    val listState = androidx.compose.foundation.lazy.rememberLazyListState()
    val scrollScope = rememberCoroutineScope()

    val body: @Composable (PaddingValues, AppRowStyle) -> Unit = { listPadding, rowStyle ->
                key(currentQuery, filterMode, refreshSignal, showSystemApps) {
                    RefreshableLazyColumn(
                        onRefresh,
                        { false },
                        onRefresh,
                        isNeedRefresh = isNeedRefresh,
                        scrollToTopSignal = refreshSignal,
                        scrollChromeState = scrollChromeState,
                        contentPadding = PaddingValues(
                            top = listPadding.calculateTopPadding() + 8.dp,
                            bottom = listPadding.calculateBottomPadding(),
                        ),
                        modifier = Modifier.fillMaxSize(),
                        listState = listState,
                    ) {
                        unavailableStatus?.let { status ->
                            item(key = "application-list-unavailable") {
                                ApplicationListUnavailable(
                                    status = status,
                                    onRetry = { isNeedRefresh = true },
                                )
                            }
                        }
                        items(items.res, { it.packageName }) {
                            rowStyle.wrap {
                                ApplicationItem(it, onAppClick, itemsInfo, showDivider = rowStyle.divider)
                            }
                        }
                    }
                }
    }

    val state = ApplicationListUiState(
        searchState = searchState,
        stats = stats,
        currentQuery = currentQuery,
        filterMode = filterMode,
        showSystemApps = showSystemApps,
    )
    val actions = ApplicationListActions(
        onSettingsClick = { showListSettingsSheet = true },
        onCloseSearch = { closeSearch() },
    )

    when (currentUiKitStyle()) {
        UiKitStyle.Miuix -> ApplicationListMiuix(
            state = state,
            actions = actions,
            listState = listState,
            scrollScope = scrollScope,
            scrollChromeState = scrollChromeState,
            contentBottomPadding = contentPadding.calculateBottomPadding(),
            body = body,
        )

        UiKitStyle.Expressive -> ApplicationListExpressive(
            state = state,
            actions = actions,
            listState = listState,
            scrollScope = scrollScope,
            scrollChromeState = scrollChromeState,
            contentBottomPadding = contentPadding.calculateBottomPadding(),
            body = body,
        )
    }

        // 列表设置：与记录页 / xinyi / xsmscode 拉齐，收进一个设置图标 → 底部 sheet。
        AppBottomSheet(
            show = showListSettingsSheet,
            onDismissRequest = { showListSettingsSheet = false },
            title = stringResource(R.string.action_list_settings),
        ) {
            StateSwitchItem(
                title = stringResource(R.string.action_show_system_apps),
                summary = "",
                checked = showSystemApps,
            ) { checked ->
                listViewModel.setShowSystemApps(checked)
            }
        }
}

@Composable
private fun ApplicationListUnavailable(
    status: ApplicationReadStatus,
    onRetry: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium, vertical = MaterialTheme.spacing.small)
            .background(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = RoundedCornerShape(16.dp),
            )
            .padding(MaterialTheme.spacing.medium),
    ) {
        Text(
            text = stringResource(R.string.app_list_unavailable_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
        )
        Text(
            text = applicationListUnavailableMessage(status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(top = MaterialTheme.spacing.extraSmall),
        )
        AppTextButton(
            text = stringResource(R.string.retry),
            onClick = onRetry,
            modifier = Modifier.align(Alignment.End),
        )
    }
}

@Composable
private fun applicationListUnavailableMessage(status: ApplicationReadStatus): String = stringResource(
    when (status) {
        ApplicationReadStatus.RUNTIME_MISSING -> R.string.app_list_unavailable_runtime_missing
        ApplicationReadStatus.PERMISSION_DENIED -> R.string.app_list_unavailable_permission_denied
        ApplicationReadStatus.BINDING -> R.string.app_list_unavailable_binding
        ApplicationReadStatus.INCOMPATIBLE,
        ApplicationReadStatus.UNSUPPORTED,
        -> R.string.app_list_unavailable_incompatible
        ApplicationReadStatus.DISCONNECTED,
        ApplicationReadStatus.TIMED_OUT,
        ApplicationReadStatus.TEMPORARILY_DISCONNECTED,
        -> R.string.app_list_unavailable_disconnected
        ApplicationReadStatus.FAILED -> R.string.app_list_unavailable_failed
    },
)

@Composable
internal fun ApplicationHeaderPills(
    stats: ApplicationStats,
    query: String,
    filterMode: Int,
    showSystemApps: Boolean = false,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        InfoPill(
            text = "${stringResource(R.string.app_list_stats_integrated)}: ${stats.usingMiPush}/${stats.total}",
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InfoPill(
            text = "${stringResource(R.string.app_list_stats_registration)}: ${stats.registered}/${stats.usingMiPush}",
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.primary,
        )
        if (query.isNotBlank()) {
            InfoPill(
                text = "${stringResource(R.string.action_search)} · $query",
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        applicationFilterLabel(filterMode)?.let { label ->
            InfoPill(
                text = label,
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (showSystemApps) {
            InfoPill(
                text = stringResource(R.string.action_show_system_apps),
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

@Composable
private fun applicationFilterLabel(filterMode: Int): String? {
    return when (filterMode) {
        1 -> stringResource(R.string.app_list_stats_registered)
        2 -> stringResource(R.string.app_list_stats_not_registered)
        3 -> stringResource(R.string.status_app_registered_error_title)
        else -> null
    }
}

@Composable
private fun ApplicationItem(
    item: ManagerApplication,
    onAppClick: (String) -> Unit,
    itemsInfo: Map<String, AppInfoForDisplay>,
    showDivider: Boolean = true,
) {
    val info = itemsInfo[item.packageName] ?: return
    val statusColor =
        if (info.registrationState.second == Color.Unspecified) MaterialTheme.colorScheme.onSurface
        else info.registrationState.second
    val isRecentlyActive = item.lastReceiveTimeMs > 0L
    val containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    val activityLabel = if (isRecentlyActive) {
        io.github.magisk317.mipush.feature.main.subpage.friendlyDateString(
            java.util.Date(item.lastReceiveTimeMs),
            java.util.Date(),
            LocalContext.current
        )
    } else {
        stringResource(R.string.app_list_item_delivery_idle)
    }

    Column(Modifier.fillMaxWidth()) {
    WorkspaceListItem(
        modifier = Modifier
            .fillMaxWidth(),
        containerColor = containerColor,
        onClick = { onAppClick(item.packageName) },
        leadingContent = {
            AppIconImage(
                packageName = item.packageName,
                modifier = Modifier.size(40.dp),
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
            text = item.appName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val registrationLabel = stringResource(info.registrationState.first)
            val prefix = if (!item.existServices) stringResource(R.string.mipush_services_not_found) + " - " else ""
            AppListBadge(
                text = prefix + registrationLabel,
                containerColor = statusColor.copy(alpha = 0.14f),
                contentColor = statusColor,
            )
            if (info.isZygiskEnabled == true) {
                AppListBadge(
                    text = "Zygisk",
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }
            AppListBadge(
                text = activityLabel,
                containerColor = if (isRecentlyActive) {
                    MaterialTheme.colorScheme.secondaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHighest
                },
                contentColor = if (isRecentlyActive) {
                    MaterialTheme.colorScheme.onSecondaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}
        if (showDivider) {
            AppHorizontalDivider(
                thickness = 0.5.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
        }
    }

@Composable
private fun AppListBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    AppBadge(
        text = text,
        containerColor = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ApplicationListPreview(
    query: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    filterMode: Int = 0,
    onAppClick: (String) -> Unit,
    items: ApplicationPageOperation.MiPushApplications,
    itemsInfo: Map<String, AppInfoForDisplay>,
    scrollChromeState: ScrollChromeState? = null,
) {
    // Preview helper mirrors the runtime shape (bar above the list) without
    // the removed overlay-hero scaffolding.
    Page {
        Column(modifier = Modifier.fillMaxSize()) {
            AppTopBar(
                title = stringResource(R.string.app_list_hero_title),
            )
            val listState = androidx.compose.foundation.lazy.rememberLazyListState()
            RefreshableLazyColumn(
                doRefresh = {},
                isNeedMore = { false },
                doLoadMore = {},
                isNeedRefresh = false,
                scrollToTopSignal = refreshSignal,
                scrollChromeState = scrollChromeState,
                contentPadding = PaddingValues(
                    top = 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + MaterialTheme.spacing.medium,
                ),
                listState = listState,
            ) {
                items(items.res, { it.packageName }) {
                    ApplicationItem(it, onAppClick, itemsInfo)
                }
            }
        }
    }
}
@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
    showSystemUi = true,
)
@Composable
fun ApplicationListPreview() {
    val miPushApplications = ApplicationPageOperation.MiPushApplications()
    miPushApplications.res = (
        mutableListOf(
            registeredApplication(
                ManagerApplication.RegisteredType.NOT_REGISTERED,
                "123"
            ),
            registeredApplication(
                ManagerApplication.RegisteredType.REGISTERED,
                "qwe"
            ),
            registeredApplication(
                ManagerApplication.RegisteredType.REGISTERED,
                "asd"
            ),
            registeredApplication(
                ManagerApplication.RegisteredType.UNREGISTERED,
                "zxc"
            ),
            registeredApplication(
                ManagerApplication.RegisteredType.UNREGISTERED,
                "456",
                false
            ),
        ) + ('a'..'z').map {
            registeredApplication(
                ManagerApplication.RegisteredType.NOT_REGISTERED,
                it.toString()
            )
        }
    ).toMutableList()
    val infoMap = miPushApplications.res.associate { app ->
        app.packageName to AppInfoForDisplay(
            registrationState = RegistrationStateStyle.contentOf(app),
            lastReceiveTime = "",
        )
    }
    ApplicationListPreview(
        onAppClick = {},
        items = miPushApplications,
        itemsInfo = infoMap,
    )
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
    showSystemUi = true,
)
@Composable
fun OneApplicationWithNonMiPushAppPreview() {
    val miPushApplications = ApplicationPageOperation.MiPushApplications()
    miPushApplications.res = mutableListOf(
        registeredApplication(
            ManagerApplication.RegisteredType.NOT_REGISTERED,
            "123"
        )
    )
    miPushApplications.totalPkg = 100
    val infoMap = miPushApplications.res.associate { app ->
        app.packageName to AppInfoForDisplay(
            registrationState = RegistrationStateStyle.contentOf(app),
            lastReceiveTime = "",
        )
    }
    ApplicationListPreview(
        onAppClick = {},
        items = miPushApplications,
        itemsInfo = infoMap,
    )
}

private fun registeredApplication(
    registeredType: Int,
    appName: String,
    existServices: Boolean = true
): ManagerApplication {
    return ManagerApplication(
        packageName = appName,
        type = ManagerApplication.Type.ASK,
        notificationOnRegister = true,
        registeredType = registeredType,
        appName = appName,
        existServices = existServices,
    )
}

/** Settings action shared by both list-tab styles (rendered in the bar slot). */
@Composable
internal fun ApplicationHeaderSettingsAction(onClick: () -> Unit) {
    AppIconButton(onClick = onClick) {
        Icon(
            imageVector = Icons.Default.Settings,
            contentDescription = stringResource(R.string.action_list_settings),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Style-agnostic render state for the application list tab
 * (KernelSU `HomeUiState` / `SuperUserUiState` model).
 */
internal data class ApplicationListUiState(
    val searchState: SearchOverlayState,
    val stats: ApplicationStats,
    val currentQuery: String,
    val filterMode: Int,
    val showSystemApps: Boolean,
)

/**
 * Row presentation knobs supplied by each style file (miuix: gapped cards, no
 * dividers, per KernelSU SimpleAppItem rhythm; expressive: full-bleed rows
 * with dividers as before).
 */
internal class AppRowStyle(
    val divider: Boolean,
    val wrap: @Composable (content: @Composable () -> Unit) -> Unit,
)

/**
 * Action callbacks assembled by the dispatcher, consumed verbatim by each
 * style implementation (KernelSU `HomeActions` model).
 */
internal class ApplicationListActions(
    val onSettingsClick: () -> Unit,
    val onCloseSearch: () -> Unit,
)
