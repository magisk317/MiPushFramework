package io.github.magisk317.mipush.feature.main.subpage

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
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.github.aakira.napier.Napier
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.hazeSource
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.manager.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.manager.ManagerApplication
import io.github.magisk317.mipush.common.utils.Utils
import androidx.compose.ui.res.stringResource
import io.github.magisk317.mipush.feature.main.MainScrollChromeState
import io.github.magisk317.mipush.feature.main.RegistrationStateStyle
import androidx.compose.material3.ExperimentalMaterial3Api
import io.github.magisk317.mipush.feature.ui.component.SearchBar
import io.github.magisk317.mipush.feature.ui.component.AppIcon
import io.github.magisk317.mipush.feature.ui.component.DetailSectionCard
import io.github.magisk317.mipush.feature.ui.component.InfoPill
import io.github.magisk317.mipush.feature.ui.component.MetricCard
import io.github.magisk317.mipush.feature.ui.component.MetricGrid
import io.github.magisk317.mipush.feature.ui.component.MetricSpec
import io.github.magisk317.mipush.feature.ui.theme.spacing
import io.github.magisk317.mipush.feature.ui.component.OverlayHeaderScaffold
import io.github.magisk317.mipush.feature.ui.component.RefreshableLazyColumn
import io.github.magisk317.mipush.feature.ui.component.ScrollToTopFAB
import io.github.magisk317.mipush.feature.ui.component.WorkspaceTopBarSearchOverlay
import io.github.magisk317.mipush.feature.ui.component.WorkspaceListItem

data class AppInfoForDisplay(
    val registrationState: Pair<Int, Color>,
    val lastReceiveTime: String,
)

private var g_itemsInfo by mutableStateOf(emptyMap<String, AppInfoForDisplay>())
private var g_items by mutableStateOf(ApplicationPageOperation.MiPushApplications())
private val TAG = "ApplicationListPage"
@Composable
fun ApplicationList(
    query: String,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    filterMode: Int = 0,
    onAppClick: (String) -> Unit,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
    scrollChromeState: MainScrollChromeState? = null,
) {
    val context = LocalContext.current
    ApplicationList(
        query = query,
        contentPadding = contentPadding,
        refreshSignal = refreshSignal,
        filterMode = filterMode,
        onAppClick = onAppClick,
        getMiPushApplications = { q, mode ->
            val miPushApplications =
                ApplicationPageOperation.getMiPushApplicationsThatQueryMatched(q, mode)
            ApplicationPageOperation.updateRegisteredApplicationDb(
                context,
                miPushApplications.res
            )
            miPushApplications
        },
        hazeState = hazeState,
        hazeStyle = hazeStyle,
        scrollChromeState = scrollChromeState,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApplicationList(
    query: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    filterMode: Int = 0,
    onAppClick: (String) -> Unit,
    getMiPushApplications: (query: String, filterMode: Int) -> ApplicationPageOperation.MiPushApplications,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null,
    scrollChromeState: MainScrollChromeState? = null,
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var currentQuery by rememberSaveable(query) { mutableStateOf(query) }
    var searchExpanded by rememberSaveable(query) { mutableStateOf(query.isNotBlank()) }

    if (isPreview) g_items = getMiPushApplications(currentQuery, filterMode)
    val shouldRefresh = g_items.res.isEmpty() || currentQuery.isNotEmpty() || refreshSignal > 0
    var isNeedRefresh by rememberSaveable(currentQuery, refreshSignal, filterMode) { mutableStateOf(shouldRefresh) }

    androidx.compose.runtime.LaunchedEffect(currentQuery, refreshSignal, filterMode) {
        if (!shouldRefresh) {
            isNeedRefresh = true
        }
    }

    val refreshScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate) }
    androidx.compose.runtime.DisposableEffect(Unit) {
        onDispose {
            refreshScope.cancel()
        }
    }
    val stats by remember {
        derivedStateOf {
            g_items.toApplicationStats()
        }
    }

    val onRefresh: (onRefreshed: () -> Unit) -> Unit = { onRefreshed ->
        refreshScope.launch(Dispatchers.IO) {
            try {
                val applications = getMiPushApplications(currentQuery, filterMode)
                updateInfos(applications, context)

                withContext(Dispatchers.Main) {
                    g_items = applications
                    isNeedRefresh = false
                    onRefreshed()
                }
                // iconCache removed, AppIcon handles caching
            } catch (e: Throwable) {
                logE("failed to load app list: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    isNeedRefresh = false
                    onRefreshed()
                }
            }
        }
    }

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, currentQuery, isPreview, isNeedRefresh) {
        if (isPreview || currentQuery.isNotEmpty()) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME || isNeedRefresh) return@LifecycleEventObserver
            refreshScope.launch(Dispatchers.IO) {
                val applications = getMiPushApplications(currentQuery, filterMode)
                updateInfos(applications, context)
                withContext(Dispatchers.Main) {
                    g_items = applications
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Page {
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val topOverlayHeight = topInset + if (searchExpanded) 152.dp else 96.dp
        val listState = androidx.compose.foundation.lazy.rememberLazyListState()
        Box(modifier = Modifier.fillMaxSize()) {
        OverlayHeaderScaffold(
            fallbackTopPadding = topOverlayHeight,
            bottomPadding = contentPadding.calculateBottomPadding() + 28.dp,
            headerVisible = (scrollChromeState?.isChromeVisible ?: true) || searchExpanded,
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
                    modifier = if (hazeState != null) {
                        Modifier
                            .fillMaxSize()
                            .hazeSource(hazeState)
                    } else {
                        Modifier.fillMaxSize()
                    },
                    listState = listState,
                ) {
                    items(g_items.res, { it.packageName }) {
                        ApplicationItem(it, onAppClick)
                    }
                }
            },
            overlay = {
                Column {
                    TopAppBar(
                        title = { Text(stringResource(R.string.app_list_hero_title)) },
                        windowInsets = WindowInsets.statusBars,
                        actions = {
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
                        )
                    }
                }
            },
        )
        ScrollToTopFAB(listState)
        }
    }
}

@Composable
private fun ApplicationHeaderPills(
    stats: ApplicationStats,
    query: String,
    filterMode: Int,
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

private fun updateInfos(
    applications: ApplicationPageOperation.MiPushApplications,
    context: Context
) {
    val infoMap = emptyMap<String, AppInfoForDisplay>().toMutableMap()
    applications.res.forEach {
        infoMap[it.packageName] = AppInfoForDisplay(
            registrationState = RegistrationStateStyle.contentOf(it),
            lastReceiveTime = if (it.lastReceiveTimeMs == 0L) ""
            else context.getString(R.string.last_receive) + friendlyDateString(
                java.util.Date(it.lastReceiveTimeMs),
                Utils.getUTC(),
                context
            ),
        )
    }
    g_itemsInfo = infoMap
}

@Composable
private fun ApplicationItem(item: ManagerApplication, onAppClick: (String) -> Unit) {
    val info = g_itemsInfo[item.packageName] ?: return
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
    val activityLabel = stringResource(
        if (isRecentlyActive) {
            R.string.app_list_item_delivery_active
        } else {
            R.string.app_list_item_delivery_idle
        }
    )

    WorkspaceListItem(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = MaterialTheme.spacing.medium),
        containerColor = containerColor,
        onClick = { onAppClick(item.packageName) },
        leadingContent = {
            AppIcon(item.packageName, item.appName, Modifier.size(44.dp))
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

@Composable
private fun LastReceive(item: ManagerApplication) {
    val info = g_itemsInfo[item.packageName] ?: return
    if (info.lastReceiveTime.isBlank()) return
    Text(
        info.lastReceiveTime,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
    showSystemUi = true,
)
@Composable
fun ApplicationListPreview() {
    // Napier.init()


    ApplicationList(
        contentPadding = PaddingValues(0.dp),
        onAppClick = {},
        getMiPushApplications = { _, _ ->
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

            miPushApplications
        }
    )
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
    showSystemUi = true,
)
@Composable
fun OneApplicationWithNonMiPushAppPreview() {
    // Napier.init()

    ApplicationList(
        onAppClick = {},
        getMiPushApplications = { _, _ ->
            val miPushApplications = ApplicationPageOperation.MiPushApplications()
            miPushApplications.res = mutableListOf(
                registeredApplication(
                    ManagerApplication.RegisteredType.NOT_REGISTERED,
                    "123"
                )
            )
            miPushApplications.totalPkg = 100
            miPushApplications
        }
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
