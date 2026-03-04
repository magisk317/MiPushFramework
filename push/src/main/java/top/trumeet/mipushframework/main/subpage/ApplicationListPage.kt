package top.trumeet.mipushframework.main.subpage

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.painterResource
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
import com.xiaomi.xmsf.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.entities.RegisteredApplication
import androidx.compose.ui.res.stringResource
import top.trumeet.mipushframework.main.RegistrationStateStyle
import top.trumeet.mipushframework.utils.ParseUtils
import androidx.compose.material3.ExperimentalMaterial3Api
import top.trumeet.mipushframework.component.SearchBar
import top.trumeet.mipushframework.component.AppIcon
import top.trumeet.ui.theme.spacing
import top.trumeet.mipushframework.component.RefreshableLazyColumn

data class AppInfoForDisplay(
    val registrationState: Pair<String, Color>,
    val lastReceiveTime: String,
)

private var g_itemsInfo by mutableStateOf(emptyMap<String, AppInfoForDisplay>())
private var g_items by mutableStateOf(ApplicationPageOperation.MiPushApplications())
private val TAG = "ApplicationListPage"
private val logger = object {
    fun d(msg: String, vararg args: Any?) {
        if (args.isEmpty()) Napier.d(msg, tag = TAG)
        else Napier.d(String.format(msg, *args), tag = TAG)
    }
    fun e(msg: String, t: Throwable? = null) {
        Napier.e(msg, t, tag = TAG)
    }
}
@Composable
fun ApplicationList(
    query: String,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    filterMode: Int = 0,
    onAppClick: (String) -> Unit,
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null
) {
    val context = LocalContext.current
    ApplicationList(
        query = query,
        contentPadding = contentPadding,
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
        hazeStyle = hazeStyle
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
    hazeStyle: HazeStyle? = null
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current
    
    var currentQuery by rememberSaveable(query) { mutableStateOf(query) }

    if (isPreview) g_items = getMiPushApplications(currentQuery, filterMode)
    val shouldRefresh = g_items.res.isEmpty() || currentQuery.isNotEmpty() || refreshSignal > 0
    var isNeedRefresh by rememberSaveable(currentQuery, refreshSignal, filterMode) { mutableStateOf(shouldRefresh) }

    androidx.compose.runtime.LaunchedEffect(currentQuery, refreshSignal, filterMode) {
        if (!shouldRefresh) {
            isNeedRefresh = true
        }
    }

    val refreshScope = rememberCoroutineScope()

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
                logger.e("failed to load app list", e)
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
        val topOverlayHeight = topInset + 72.dp
        Box(modifier = Modifier.fillMaxSize()) {
            RefreshableLazyColumn(
                onRefresh,
                { false },
                onRefresh,
                isNeedRefresh,
                scrollToTopSignal = refreshSignal,
                contentPadding = PaddingValues(
                    top = topOverlayHeight + 8.dp,
                    bottom = contentPadding.calculateBottomPadding() + 28.dp
                ),
                modifier = if (hazeState != null) {
                    Modifier
                        .fillMaxSize()
                        .hazeSource(hazeState)
                } else {
                    Modifier.fillMaxSize()
                }
            ) {
                items(g_items.res, { it.packageName }) {
                    ApplicationItem(it, onAppClick)
                }
                item {
                    val notUseMiPushCount by remember { derivedStateOf { g_items.totalPkg - g_items.res.size } }
                    Footer(notUseMiPushCount)
                }
            }

            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))
                    .then(
                        if (hazeState != null && hazeStyle != null) {
                            Modifier.hazeEffect(hazeState, hazeStyle) {
                                forceInvalidateOnPreDraw = true
                            }
                        } else {
                            Modifier
                        }
                    )
            ) {
                SearchBar(
                    placeholder = stringResource(android.R.string.search_go),
                    query = currentQuery,
                    onValueChange = { currentQuery = it },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

private fun updateInfos(
    applications: ApplicationPageOperation.MiPushApplications,
    context: Context
) {
    val infoMap = emptyMap<String, AppInfoForDisplay>().toMutableMap()
    applications.res.forEach {
        infoMap[it.packageName] = AppInfoForDisplay(
            registrationState = RegistrationStateStyle.contentOf(it, context),
            lastReceiveTime = if (it.lastReceiveTime.time == 0L) ""
            else context.getString(R.string.last_receive) + ParseUtils.getFriendlyDateString(
                it.lastReceiveTime,
                Utils.getUTC(),
                context
            ),
        )
    }
    g_itemsInfo = infoMap
}

@Composable
private fun Footer(notUseMiPushCount: Int) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            painterResource(R.drawable.ic_info_outline_black_24dp),
            null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(10.dp)
        )
        Text(
            ApplicationPageOperation.getNotSupportHint(
                context,
                notUseMiPushCount
            ),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ApplicationItem(item: RegisteredApplication, onAppClick: (String) -> Unit) {
    val context = LocalContext.current
    val info = g_itemsInfo[item.packageName] ?: return
    val statusColor =
        if (info.registrationState.second == Color.Unspecified) MaterialTheme.colorScheme.onSurface
        else info.registrationState.second

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                horizontal = MaterialTheme.spacing.medium,
                vertical = MaterialTheme.spacing.small
            )
            .clickable { onAppClick(item.packageName) },
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(item.packageName, item.appName, Modifier.size(48.dp))
            Spacer(Modifier.width(MaterialTheme.spacing.medium))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = item.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = statusColor
                )
                LastReceive(item)
            }
            Spacer(Modifier.width(MaterialTheme.spacing.small))
            Text(
                text = info.registrationState.first,
                style = MaterialTheme.typography.labelMedium,
                color = statusColor
            )
        }
    }
}

@Composable
private fun LastReceive(item: RegisteredApplication) {
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
                        RegisteredApplication.RegisteredType.NotRegistered,
                        "123"
                    ),
                    registeredApplication(
                        RegisteredApplication.RegisteredType.Registered,
                        "qwe"
                    ),
                    registeredApplication(
                        RegisteredApplication.RegisteredType.Registered,
                        "asd"
                    ),
                    registeredApplication(
                        RegisteredApplication.RegisteredType.Unregistered,
                        "zxc"
                    ),
                    registeredApplication(
                        RegisteredApplication.RegisteredType.Unregistered,
                        "456",
                        false
                    ),
                ) + ('a'..'z').map {
                    registeredApplication(
                        RegisteredApplication.RegisteredType.NotRegistered,
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
                    RegisteredApplication.RegisteredType.NotRegistered,
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
): RegisteredApplication {
    val registeredApplication =
        RegisteredApplication(
            null,
            appName,
            RegisteredApplication.Type.ASK,
            true,
            registeredType,
            appName
        )
    registeredApplication.existServices = existServices
    return registeredApplication
}
