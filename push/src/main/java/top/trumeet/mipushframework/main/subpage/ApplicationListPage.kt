package top.trumeet.mipushframework.main.subpage

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
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
import com.elvishew.xlog.XLog
import com.xiaomi.xmsf.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipushframework.component.AppIcon
import top.trumeet.mipushframework.component.RefreshableLazyColumn
import top.trumeet.mipushframework.main.RegistrationStateStyle
import top.trumeet.mipushframework.utils.ParseUtils

data class AppInfoForDisplay(
    val registrationState: Pair<String, Color>,
    val lastReceiveTime: String,
)

private var g_itemsInfo by mutableStateOf(emptyMap<String, AppInfoForDisplay>())
private var g_items by mutableStateOf(ApplicationPageOperation.MiPushApplications())
private val logger = XLog.tag("ApplicationListPage").build()

@Composable
fun ApplicationList(
    query: String,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    filterMode: Int = 0,
    onAppClick: (String) -> Unit
) {
    val context = LocalContext.current
    ApplicationList(
        query = query,
        contentPadding = contentPadding,
        filterMode = filterMode,
        onAppClick = onAppClick
    ) { mode ->
        val miPushApplications =
            ApplicationPageOperation.getMiPushApplicationsThatQueryMatched(query, mode)
        ApplicationPageOperation.updateRegisteredApplicationDb(
            context,
            miPushApplications.res
        )
        miPushApplications
    }
}

@Composable
fun ApplicationList(
    query: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    filterMode: Int = 0,
    onAppClick: (String) -> Unit,
    getMiPushApplications: (filterMode: Int) -> ApplicationPageOperation.MiPushApplications
) {
    val context = LocalContext.current
    val isPreview = LocalInspectionMode.current
    val lifecycleOwner = LocalLifecycleOwner.current
    if (isPreview) g_items = getMiPushApplications(filterMode)
    val shouldRefresh = g_items.res.isEmpty() || query.isNotEmpty() || refreshSignal > 0
    var isNeedRefresh by rememberSaveable(query, refreshSignal, filterMode) { mutableStateOf(shouldRefresh) }

    androidx.compose.runtime.LaunchedEffect(query, refreshSignal, filterMode) {
        if (!shouldRefresh) {
            isNeedRefresh = true
        }
    }

    val refreshScope = rememberCoroutineScope()

    val onRefresh: (onRefreshed: () -> Unit) -> Unit = { onRefreshed ->
        refreshScope.launch(Dispatchers.IO) {
            try {
                val applications = getMiPushApplications(filterMode)
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

    androidx.compose.runtime.DisposableEffect(lifecycleOwner, query, isPreview, isNeedRefresh) {
        if (isPreview || query.isNotEmpty()) {
            return@DisposableEffect onDispose { }
        }
        val observer = LifecycleEventObserver { _, event ->
            if (event != Lifecycle.Event.ON_RESUME || isNeedRefresh) return@LifecycleEventObserver
            refreshScope.launch(Dispatchers.IO) {
                val applications = getMiPushApplications(filterMode)
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
        RefreshableLazyColumn(
            onRefresh,
            { false },
            onRefresh,
            isNeedRefresh,
            scrollToTopSignal = refreshSignal,
            contentPadding = contentPadding
        ) {
            items(g_items.res, { it.packageName }) {
                ApplicationItem(it, onAppClick)
            }
            item {
                val notUseMiPushCount by remember { derivedStateOf { g_items.totalPkg - g_items.res.size } }
                Footer(notUseMiPushCount)
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

    Row(
        Modifier
            .clickable {
                onAppClick(item.packageName)
            }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppIcon(item.packageName, item.appName, Modifier.size(48.dp))
        Spacer(Modifier.width(20.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                item.appName,
                style = MaterialTheme.typography.bodyLarge,
                color = statusColor
            )
            LastReceive(item)
        }
        Spacer(Modifier.width(12.dp))
        Text(
            info.registrationState.first,
            style = MaterialTheme.typography.bodyMedium,
            color = statusColor
        )
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
    // XLog.init()


    ApplicationList(contentPadding = PaddingValues(0.dp), onAppClick = {}) { _ ->
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
        }).toMutableList()

        miPushApplications
    }
}

@Preview(
    showBackground = true,
    device = Devices.PIXEL_3,
    showSystemUi = true,
)
@Composable
fun OneApplicationWithNonMiPushAppPreview() {
    // XLog.init()

    ApplicationList(onAppClick = {}) { _ ->
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
