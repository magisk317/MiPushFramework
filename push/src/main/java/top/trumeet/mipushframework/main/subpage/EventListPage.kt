@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package top.trumeet.mipushframework.main.subpage

import android.content.Intent
import android.net.Uri
import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
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
import com.magisk317.Global
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.push.utils.RegSecUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.trumeet.common.Constants
import top.trumeet.common.utils.Utils
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.event.type.TypeFactory
import top.trumeet.mipushframework.component.AppIcon
import top.trumeet.mipushframework.component.DialogAction
import top.trumeet.mipushframework.component.DialogActionRow
import top.trumeet.mipushframework.component.RefreshableLazyColumn
import top.trumeet.mipushframework.component.SearchBar
import top.trumeet.mipushframework.component.TextView
import top.trumeet.mipushframework.main.RecentEventListPage
import top.trumeet.mipushframework.utils.ParseUtils
import java.text.SimpleDateFormat
import java.util.Date

import androidx.hilt.navigation.compose.hiltViewModel
import com.magisk317.main.viewmodel.EventListViewModel

private val receiveDateFormat: SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

@Composable
fun EventList(
    query: String = "",
    packageName: String = "",
    contentPadding: PaddingValues = PaddingValues(0.dp),
    refreshSignal: Int = 0,
    groupByApp: Boolean = false,
    viewModel: EventListViewModel = hiltViewModel(),
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null
) {
    Page {
        val context = LocalContext.current
        var clickedEvent by remember { mutableStateOf<EventInfoForDisplay?>(null) }
        var currentQuery by rememberSaveable(query) { mutableStateOf(query) }
        var isGroupByApp by rememberSaveable { mutableStateOf(groupByApp) }
        val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val listPadding = PaddingValues(
            top = topInset + 72.dp + 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 28.dp
        )

        clickedEvent?.let {
            EventDetailsDialog(it, viewModel = viewModel) { clickedEvent = null }
        }

        Box(modifier = Modifier.fillMaxSize()) {
            // 列表内容
            if (isGroupByApp && packageName.isEmpty()) {
                EventGroupList(
                    query = currentQuery,
                    refreshSignal = refreshSignal,
                    contentPadding = listPadding,
                    viewModel = viewModel,
                    hazeState = hazeState
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
                    contentPadding = listPadding,
                    hazeState = hazeState,
                    hazeStyle = hazeStyle
                )
            }

            val topBarModifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.62f))

            TopAppBar(
                title = {
                    SearchBar(
                        placeholder = stringResource(android.R.string.search_go),
                        query = currentQuery,
                        onValueChange = { currentQuery = it },
                        modifier = Modifier.fillMaxWidth()
                    )
                },
                actions = {
                    IconButton(onClick = { isGroupByApp = !isGroupByApp }) {
                        Icon(
                            painter = painterResource(
                                id = if (isGroupByApp) {
                                    R.drawable.ic_event_note_black_24dp
                                } else {
                                    R.drawable.ic_apps_black_24dp
                                }
                            ),
                            contentDescription = if (isGroupByApp) "Show Events" else "Group by App"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                ),
                modifier = if (hazeState != null && hazeStyle != null) {
                    topBarModifier.hazeEffect(hazeState, hazeStyle) {
                        forceInvalidateOnPreDraw = true
                    }
                } else {
                    topBarModifier
                }
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
private fun EventGroupList(
    query: String,
    refreshSignal: Int,
    contentPadding: PaddingValues,
    viewModel: EventListViewModel,
    hazeState: HazeState? = null
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
        contentPadding = contentPadding,
        modifier = if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier
    ) {
        items(groupedItems, key = { it.packageName }) { group ->
            val updatedAt = ParseUtils.getFriendlyDateString(
                group.latestDate,
                Utils.getUTC(),
                context
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
                    .clickable {
                        context.startActivity(
                            Intent(context, RecentEventListPage::class.java)
                                .setData(Uri.parse(group.packageName))
                        )
                    },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                ),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    AppIcon(group.packageName, group.appName, modifier = Modifier.size(48.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            group.appName,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            "${group.events.size} 条记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        "更新于$updatedAt",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
            content ?: viewModel.getJson(clickedEvent.event)
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
                            RegSecUtils.getContainerWithRegSec(clickedEvent.event)?.let {
                                viewModel.mockMessage(it)
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
                Text("Developer Info", style = MaterialTheme.typography.titleLarge)
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
fun EventList(
    onClick: (EventInfoForDisplay) -> Unit,
    getEvents: suspend (isRefresh: Boolean) -> List<EventInfoForDisplay>,
    query: String,
    packageName: String,
    refreshSignal: Int = 0,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    hazeState: HazeState? = null,
    hazeStyle: HazeStyle? = null
) {
    val isPreview = LocalInspectionMode.current
    val items = remember {
        if (packageName.isEmpty() && !isPreview) g_items
        else mutableStateListOf()
    }


    val refreshScope = rememberCoroutineScope { Dispatchers.IO }
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

    RefreshableLazyColumn(
        doRefresh,
        isNeedMore,
        doLoadMore,
        isNeedRefresh,
        scrollToTopSignal = refreshSignal,
        contentPadding = contentPadding,
        modifier = if (hazeState != null) {
            Modifier.hazeSource(hazeState)
        } else {
            Modifier
        }
    ) {
        if (items.isEmpty() && !isLoading) {
            item {
                EmptyEventState(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 300.dp)
                )
            }
        } else {
            items(items, key = { it.composeKey() }) {
                EventItem(it, onClick)
            }
        }
    }
}

@Composable
private fun EventItem(item: EventInfoForDisplay, onClick: (EventInfoForDisplay) -> Unit) {
    val disabled = item.configOptions.contains("disable")
    val alpha = if (disabled) 0.5f else 1f
    val iconSize = 48.dp
    val iconGap = 20.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .clickable { onClick(item) }
            .alpha(alpha),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                AppIcon(item.packageName, item.appName, modifier = Modifier.size(iconSize))
                Spacer(Modifier.width(iconGap))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        EventHeaderMetaLine1(item, modifier = Modifier.weight(1f))
                        Spacer(Modifier.width(10.dp))
                        EventReceiveDate(item)
                    }
                    EventHeaderTitle(item)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = iconSize + iconGap, top = 8.dp)
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    EventContent(item)
                }
            }
        }
    }
}

@Composable
private fun EventHeaderMetaLine1(item: EventInfoForDisplay, modifier: Modifier = Modifier) {
    val appName = item.appName?.takeIf { it.isNotBlank() } ?: item.packageName
    val merged = buildString {
        append(appName)
        if (item.channel.isNotBlank()) {
            append(" · ")
            append(item.channel)
        }
    }
    Text(
        text = merged,
        modifier = modifier,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1
    )
}

@Composable
private fun EventHeaderTitle(item: EventInfoForDisplay) {
    Text(
        text = item.title,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurface,
        maxLines = 1
    )
}

@Composable
private fun EventReceiveDate(item: EventInfoForDisplay) {
    val format = receiveDateFormat
    Text(
        format.format(item.receiveDate),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun EventContent(item: EventInfoForDisplay) {
    Text(
        item.content,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/**
 * 空状态UI - 当没有日志时显示
 */
@Composable
fun EmptyEventState(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // 空态图标
        Icon(
            painter = painterResource(id = R.drawable.ic_event_note_black_24dp),
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .alpha(0.3f),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        // 标题
        Text(
            "暂无日志",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        
        // 描述文本
        Text(
            "等待应用推送消息或手动触发测试",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
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
        EventList({ }, getEvents, "", "", contentPadding = PaddingValues(0.dp))
    }
}

private fun date(year: Int, month: Int, date: Int) = Date(year - 1900, month - 1, date)


data class EventInfoForDisplay(
    val id: Long,
    val packageName: String,
    val configOptions: Set<String>,
    val channel: String,
    val receiveDate: Date,
    val title: String,
    val content: String,
    val appName: String? = null,
    val event: Event = Event(),
)
