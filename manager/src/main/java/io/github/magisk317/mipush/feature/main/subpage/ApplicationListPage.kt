package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.common.R as CommonR
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.main.viewmodel.ApplicationListViewModel
import io.github.magisk317.mipush.manager.application.ApplicationReadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.manager.ManagerApplicationGateway
import io.github.magisk317.mipush.common.utils.Utils
import androidx.compose.ui.res.stringResource
import io.github.magisk317.uikit.scroll.ScrollChromeState
import io.github.magisk317.mipush.feature.main.RegistrationStateStyle
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.magisk317.uikit.surface.AppIconImage
import io.github.magisk317.mipush.feature.ui.component.RefreshableLazyColumn
import io.github.magisk317.uikit.surface.ScrollToTopFAB
import io.github.magisk317.uikit.surface.InfoPill
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.uikit.surface.DetailSectionCard
import io.github.magisk317.uikit.surface.MetricCard
import io.github.magisk317.uikit.surface.MetricGrid
import io.github.magisk317.uikit.surface.MetricSpec
import io.github.magisk317.uikit.surface.OverlayHeaderScaffold
import io.github.magisk317.uikit.surface.WorkspaceTopBarSearchOverlay
import io.github.magisk317.uikit.surface.WorkspaceListItem
import io.github.magisk317.uikit.surface.chromeTopAppBarColors
import io.github.magisk317.uikit.surface.AppBottomSheet
import io.github.magisk317.uikit.preference.StateSwitchItem
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel

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
    var searchExpanded by rememberSaveable(query) { mutableStateOf(query.isNotBlank()) }

    // Auto-load only when cache miss (first enter / search / filter / external refreshSignal).
    // Tab re-enter with same query+filter and non-empty VM cache skips IO; pull-to-refresh always loads.
    var isNeedRefresh by remember { mutableStateOf(false) }
    var handledRefreshSignal by rememberSaveable { mutableIntStateOf(0) }

    // showSystemApps toggles reload inside ViewModel.setShowSystemApps to avoid double IO.
    // currentQuery in keys keeps search live; cache hit short-circuits.
    LaunchedEffect(isActive, currentQuery, refreshSignal, filterMode) {
        if (!isActive) return@LaunchedEffect
        withFrameNanos { }
        val forceBySignal = refreshSignal > handledRefreshSignal
        if (!forceBySignal && listViewModel.hasCachedList(currentQuery, filterMode)) {
            isNeedRefresh = false
            return@LaunchedEffect
        }
        isNeedRefresh = true
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

    Page {
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val searchActive = searchExpanded || currentQuery.isNotBlank()
        val topOverlayHeight = topInset + if (searchActive) 152.dp else 96.dp
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
        OverlayHeaderScaffold(
            fallbackTopPadding = topOverlayHeight,
            bottomPadding = contentPadding.calculateBottomPadding() + 28.dp,
            headerOffsetY = scrollChromeState?.animatedHeaderOffsetY ?: 0f,
            onHeaderHeightChanged = { scrollChromeState?.headerHeightPx = it.toFloat() },
            overlayModifier = Modifier
                .fillMaxWidth(),
            content = { listPadding ->
                RefreshableLazyColumn(
                    onRefresh,
                    { false },
                    onRefresh,
                    isNeedRefresh,
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
                        ApplicationItem(it, onAppClick, itemsInfo)
                    }
                }
            },
            overlay = {
                WorkspaceTopBarSearchOverlay(
                    title = stringResource(R.string.app_list_hero_title),
                    searchQuery = currentQuery,
                    searchPlaceholder = stringResource(android.R.string.search_go),
                    searchVisible = searchActive,
                    searchActionContentDescription = stringResource(R.string.action_search),
                    onSearchActionClick = { searchExpanded = !searchExpanded },
                    actions = {
                        IconButton(onClick = { showListSettingsSheet = true }) {
                            Icon(
                                painter = painterResource(CommonR.drawable.ic_settings_black_24dp),
                                contentDescription = stringResource(R.string.action_list_settings),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    preSearchContent = {
                    Text(
                        text = stringResource(
                            R.string.app_list_hero_summary,
                            stats.usingMiPush,
                            stats.total,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(
                            start = MaterialTheme.spacing.medium,
                            end = MaterialTheme.spacing.medium,
                            bottom = MaterialTheme.spacing.small,
                        ),
                    )
                    },
                    supportingContent = {
                    Column(
                        modifier = Modifier.padding(
                            start = MaterialTheme.spacing.medium,
                            top = MaterialTheme.spacing.small,
                            end = MaterialTheme.spacing.medium,
                            bottom = MaterialTheme.spacing.small,
                        ),
                    ) {
                        ApplicationHeaderPills(
                            stats = stats,
                            query = currentQuery,
                            filterMode = filterMode,
                            showSystemApps = showSystemApps,
                        )
                    }
                    },
                    onSearchChange = { currentQuery = it },
                )
            },
        )
        ScrollToTopFAB(listState, visible = scrollChromeState?.isChromeVisible != true, extraBottomPadding = 80.dp)

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
        TextButton(
            onClick = onRetry,
            modifier = Modifier.align(Alignment.End),
        ) {
            Text(stringResource(R.string.retry))
        }
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
private fun ApplicationHeaderPills(
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
            text = "${stringResource(R.string.app_list_stats_total)} · ${stats.total}",
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        InfoPill(
            text = "${stringResource(R.string.app_list_stats_registered)} · ${stats.registered}",
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
private fun ApplicationItem(item: ManagerApplication, onAppClick: (String) -> Unit, itemsInfo: Map<String, AppInfoForDisplay>) {
    val info = itemsInfo[item.packageName] ?: return
    val statusColor =
        if (info.registrationState.second == Color.Unspecified) MaterialTheme.colorScheme.onSurface
        else info.registrationState.second
    val isRecentlyActive = item.lastReceiveTimeMs > 0L
    val containerColor = when {
        isRecentlyActive -> statusColor.copy(alpha = 0.10f)
        item.registeredType == ManagerApplication.RegisteredType.REGISTERED -> {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)
        }
        item.registeredType == ManagerApplication.RegisteredType.UNREGISTERED -> {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.18f)
        }

        else -> Color.Transparent
    }
    val activityLabel = if (isRecentlyActive) {
        io.github.magisk317.mipush.feature.main.subpage.friendlyDateString(
            java.util.Date(item.lastReceiveTimeMs),
            java.util.Date(),
            LocalContext.current
        )
    } else {
        stringResource(R.string.app_list_item_delivery_idle)
    }

    WorkspaceListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium),
        containerColor = containerColor,
        onClick = { onAppClick(item.packageName) },
        leadingContent = {
            AppIconImage(item.packageName, item.appName, Modifier.size(44.dp))
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
            text = item.appName,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
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

@Composable
private fun AppListBadge(
    text: String,
    containerColor: Color,
    contentColor: Color,
) {
    androidx.compose.material3.Surface(
        color = containerColor,
        contentColor = contentColor,
        shape = RoundedCornerShape(999.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
        )
    }
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
    val isPreview = LocalInspectionMode.current
    var currentQuery by rememberSaveable(query) { mutableStateOf(query) }
    var searchExpanded by rememberSaveable(query) { mutableStateOf(query.isNotBlank()) }
    val stats = items.toApplicationStats()

    Page {
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topOverlayHeight = topInset + if (searchExpanded) 152.dp else 96.dp
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
            OverlayHeaderScaffold(
                fallbackTopPadding = topOverlayHeight,
                bottomPadding = contentPadding.calculateBottomPadding() + 28.dp,
                headerOffsetY = 0f,
                onHeaderHeightChanged = {},
                overlayModifier = Modifier.fillMaxWidth(),
                content = { listPadding ->
                    RefreshableLazyColumn(
                        doRefresh = {},
                        isNeedMore = { false },
                        doLoadMore = {},
                        isNeedRefresh = false,
                        scrollToTopSignal = refreshSignal,
                        scrollChromeState = scrollChromeState,
                        contentPadding = PaddingValues(
                            top = listPadding.calculateTopPadding() + 8.dp,
                            bottom = listPadding.calculateBottomPadding(),
                        ),
                        listState = listState,
                    ) {
                        items(items.res, { it.packageName }) {
                            ApplicationItem(it, onAppClick, itemsInfo)
                        }
                    }
                },
                overlay = {
                    Column {
                        TopAppBar(
                            title = { Text(stringResource(R.string.app_list_hero_title)) },
                            windowInsets = WindowInsets.statusBars,
                            colors = chromeTopAppBarColors(),
                        )
                    }
                },
            )
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
