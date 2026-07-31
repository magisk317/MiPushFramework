package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.ManagerDayCount
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.manager.events.RemoteEventListSource
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.preferences.RuntimePreferenceGateway
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.manager.events.EventListRequest
import io.github.magisk317.mipush.manager.events.EventReadResult
import io.github.magisk317.mipush.common.notification.MockReplayOutcome
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
import java.util.Date
import io.github.magisk317.mipush.manager.SettingsManager

class EventListViewModel constructor(
    private val eventSource: RemoteEventListSource,
    private val eventGateway: ManagerEventGateway,
    private val settingsManager: SettingsManager,
    private val preferenceRepository: PreferenceRepository,
    private val context: Context,
    private val runtimeClient: ManagerRuntimeClient,
    private val runtimePreferenceGateway: RuntimePreferenceGateway,
) : ViewModel() {
    private val _events = MutableStateFlow<List<EventInfoForDisplay>>(emptyList())
    val events: StateFlow<List<EventInfoForDisplay>> = _events.asStateFlow()


    /** Bumped when runtime becomes Available after a gap so Event pages reload. */
    private val _runtimeReadySignal = MutableStateFlow(0)
    val runtimeReadySignal: StateFlow<Int> = _runtimeReadySignal.asStateFlow()

    init {
        viewModelScope.launch {
            var sawUnavailable = false
            runtimeClient.availability.collect { availability ->
                if (availability is ManagerRuntimeAvailability.Available) {
                    if (sawUnavailable) {
                        sawUnavailable = false
                        invalidateEventListSnapshot()
                        _runtimeReadySignal.value = _runtimeReadySignal.value + 1
                    }
                } else {
                    sawUnavailable = true
                }
            }
        }
    }

    /**
     * In-memory list snapshot for tab reuse.
     * Valid for the exact (query, packageName, refreshSignal) triple that loaded it.
     * Search / package filter / external refreshSignal always miss and reload.
     */
    data class EventListSnapshot(
        val events: List<EventInfoForDisplay>,
        val lastId: Long?,
        val hasMore: Boolean,
    )

    @Volatile private var listSnapshot: EventListSnapshot? = null
    @Volatile private var snapshotQuery: String = ""
    @Volatile private var snapshotPackageName: String = ""
    @Volatile private var snapshotRefreshSignal: Int = -1
    @Volatile private var snapshotValid: Boolean = false

    fun getEventListSnapshot(query: String, packageName: String, refreshSignal: Int): EventListSnapshot? {
        if (!snapshotValid) return null
        if (snapshotQuery != query || snapshotPackageName != packageName || snapshotRefreshSignal != refreshSignal) {
            return null
        }
        return listSnapshot
    }

    fun putEventListSnapshot(
        query: String,
        packageName: String,
        refreshSignal: Int,
        events: List<EventInfoForDisplay>,
        lastId: Long?,
        hasMore: Boolean,
    ) {
        snapshotQuery = query
        snapshotPackageName = packageName
        snapshotRefreshSignal = refreshSignal
        listSnapshot = EventListSnapshot(events = events.toList(), lastId = lastId, hasMore = hasMore)
        snapshotValid = true
    }

    fun invalidateEventListSnapshot() {
        snapshotValid = false
        listSnapshot = null
    }

    /** 事件记录保留天数;设置页/记录页共用这一份状态。 */
    val eventRetentionDays: StateFlow<Int> = preferenceRepository.eventRetentionDays
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 7)

    /** Runtime-first update; the local DataStore is only a mirror for manager UI state. */
    fun setEventRetentionDays(days: Int, onResult: ((Boolean) -> Unit)? = null) {
        val coerced = days.coerceAtLeast(1)
        viewModelScope.launch {
            val success = withContext(Dispatchers.IO) {
                runtimePreferenceGateway.setEventRetentionDays(coerced)
            }
            onResult?.invoke(success)
        }
    }

    fun loadEvents(query: String, packageName: String, isRefresh: Boolean, lastId: Long?) {
        viewModelScope.launch {
            val request = EventListRequest(
                lastId = if (isRefresh) null else lastId,
                pageSize = Constants.PAGE_SIZE,
                packageName = packageName,
                query = query,
            )
            try {
                val primary = withContext(Dispatchers.IO) {
                    loadEventsRemote(request)
                }
                val loadedEvents = primary.map { toEventInfoForDisplay(it) }
                if (isRefresh) {
                    _events.value = loadedEvents
                } else {
                    _events.value = _events.value + loadedEvents
                }
            } catch (error: RuntimeReadUnavailableException) {
                logW("loadEvents unavailable op=${error.operation} status=${error.status}")
                // Keep previous events; do not replace with empty.
            } catch (error: Exception) {
                logW("loadEvents failed: ${error.message}")
            }
        }
    }




    private suspend fun loadEventsRemote(request: EventListRequest): List<ManagerEvent> {
        return when (val result = eventSource.load(request)) {
            is EventReadResult.Available -> result.value
            is EventReadResult.Unavailable -> emptyList()
        }
    }

    private fun toEventInfoForDisplay(it: ManagerEvent): EventInfoForDisplay {
        return EventInfoForDisplay(
            id = it.id,
            packageName = it.packageName,
            configOptions = it.configOptions,
            channel = it.channel,
            receiveDate = Date(it.receiveDateMs),
            title = it.title,
            content = it.content,
            appName = it.appName,
            event = it,
        )
    }

    fun startManagePermissions(packageName: String) {
        eventGateway.startManagePermissions(packageName)
    }

    fun startConfigPreview(packageName: String) {
        viewModelScope.launch {
            eventGateway.startConfigPreview(packageName)
        }
    }
    
    fun copyToClipboard(content: String) {
        eventGateway.copyToClipboard(content)
    }
    
    suspend fun mockMessage(event: ManagerEvent): MockReplayOutcome = withContext(Dispatchers.IO) {
        eventGateway.mockMessage(event)
    }
    
    fun getContent(event: ManagerEvent): String {
        return eventGateway.getContent(event)
    }

    fun getJson(event: ManagerEvent): String? {
        return eventGateway.getJson(event)
    }
    
    suspend fun fetchEventsSuspend(isRefresh: Boolean, lastId: Long?, packageName: String, query: String): List<EventInfoForDisplay> {
        return withContext(Dispatchers.IO) {
            val request = EventListRequest(
                lastId = if (isRefresh) null else lastId,
                pageSize = Constants.PAGE_SIZE,
                packageName = packageName,
                query = query,
            )
            try {
                loadEventsRemote(request).map { toEventInfoForDisplay(it) }
            } catch (error: RuntimeReadUnavailableException) {
                logW("fetchEvents unavailable op=${error.operation} status=${error.status}")
                throw error
            }
        }
    }

    suspend fun deleteEvent(item: EventInfoForDisplay): Boolean {
        return withContext(Dispatchers.IO) {
            eventGateway.deleteEvent(item.event)
        }
    }

    suspend fun restoreEvent(item: EventInfoForDisplay): EventInfoForDisplay? {
        return withContext(Dispatchers.IO) {
            eventGateway.restoreEvent(item.event)?.let { toEventInfoForDisplay(it) }
        }
    }

    fun clearHistory() {
        invalidateEventListSnapshot()
        settingsManager.clearHistory(context, viewModelScope)
    }

    /** 按本地日历日聚合的可清理事件条数,key 形如 "2026-07-13"(设备时区)。供日历清理界面。 */
    suspend fun loadDayCounts(): Map<String, Int> {
        return withContext(Dispatchers.IO) {
            eventGateway.countEventsByDay().associate { it.day to it.count }
        }
    }

    /** 清理某时间点之前的可清理事件(注册态保留),返回删除条数。 */
    suspend fun clearHistoryBefore(cutoffMillis: Long): Int {
        invalidateEventListSnapshot()
        return withContext(Dispatchers.IO) {
            eventGateway.clearHistoryBefore(cutoffMillis)
        }
    }

    /** 清理 [startMillis, endMillis) 区间内的可清理事件(注册态保留),返回删除条数。 */
    suspend fun clearHistoryInRange(startMillis: Long, endMillis: Long): Int {
        invalidateEventListSnapshot()
        return withContext(Dispatchers.IO) {
            eventGateway.clearHistoryInRange(startMillis, endMillis)
        }
    }
}
