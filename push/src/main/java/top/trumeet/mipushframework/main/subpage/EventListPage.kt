@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package top.trumeet.mipushframework.main.subpage

import android.content.Intent
import android.net.Uri
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Devices
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.elvishew.xlog.XLog
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
import top.trumeet.mipushframework.component.RefreshableLazyColumn
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
    viewModel: EventListViewModel = hiltViewModel()
) {
    Page {
        val context = LocalContext.current
        var clickedEvent by remember { mutableStateOf<EventInfoForDisplay?>(null) }

        clickedEvent?.let {
            EventDetailsDialog(it, viewModel = viewModel) { clickedEvent = null }
        }

        if (groupByApp && packageName.isEmpty()) {
            EventGroupList(
                query = query,
                refreshSignal = refreshSignal,
                contentPadding = contentPadding,
                viewModel = viewModel
            )
        } else {
            var lastId by rememberSaveable(refreshSignal) { mutableStateOf<Long?>(null) }
            // Using the overload EventList. We need to adapt it to use ViewModel.
            // But the overload EventList takes `getEvents` lambda.
            // We can adapt ViewModel to provide data.
            // Actually, let's just make the overload EventList use ViewModel too.
            val events = remember { mutableStateListOf<EventInfoForDisplay>() }
            
            EventList(onClick = { clickedEvent = it }, getEvents = { isRefresh ->
                // This lambda is called to fetch data.
                if (isRefresh) lastId = null
                // We need to call suspending function here? 
                // The overload EventList calls this inside coroutine scope.
                // So we can block (suspend).
                // But ViewModel functions are async launch usually.
                // We need `suspend fun getEvents(...)` in ViewModel?
                // Or expose repository methods via ViewModel?
                // Let's expose a suspend helper in ViewModel.
                viewModel.fetchEventsSuspend(isRefresh, lastId, packageName, query).also { list ->
                     list.lastOrNull()?.let { lastId = it.id }
                }
            }, query, packageName, refreshSignal = refreshSignal, contentPadding = contentPadding)
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
    viewModel: EventListViewModel
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
        contentPadding = contentPadding
    ) {
        items(groupedItems, key = { it.packageName }) { group ->
            val updatedAt = ParseUtils.getFriendlyDateString(
                group.latestDate,
                Utils.getUTC(),
                context
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        context.startActivity(
                            Intent(context, RecentEventListPage::class.java)
                                .setData(Uri.parse(group.packageName))
                        )
                    }
                    .padding(horizontal = 10.dp, vertical = 8.dp),
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
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                TextButton({
                    val container = RegSecUtils.getContainerWithRegSec(clickedEvent.event)
                    if (container != null) {
                        json = viewModel.getContent(clickedEvent.event, container)
                    }
                }) { Text(stringResource(R.string.action_configurate)) }

                TextButton({
                    viewModel.copyToClipboard(json)
                }) { Text(stringResource(android.R.string.copy)) }

                TextButton({
                    RegSecUtils.getContainerWithRegSec(clickedEvent.event)?.let {
                        viewModel.mockMessage(it)
                    }
                }) { Text(stringResource(R.string.action_notify)) }
            }
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

                TextButton({
                    viewModel.startManagePermissions(clickedEvent.packageName)
                }) { Text(stringResource(R.string.action_app_info)) }
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
    contentPadding: PaddingValues = PaddingValues(0.dp)
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
        contentPadding = contentPadding
    ) {
        items(items, key = { it.composeKey() }) {
            EventItem(it, onClick)
        }
    }
}

@Composable
private fun EventItem(item: EventInfoForDisplay, onClick: (EventInfoForDisplay) -> Unit) {
    val disabled = item.configOptions.contains("disable")
    val alpha = if (disabled) 0.5f else 1f
    val iconSize = 48.dp
    val iconGap = 20.dp

    Column(
        Modifier
            .clickable { onClick(item) }
            .fillMaxWidth()
            .padding(10.dp)
            .alpha(alpha)
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
                .padding(start = iconSize + iconGap)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                EventContent(item)
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
    XLog.init()
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
