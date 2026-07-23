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
import io.github.magisk317.mipush.manager.events.ComparingEventListSource
import io.github.magisk317.mipush.manager.events.EventListComparison
import io.github.magisk317.mipush.manager.events.EventListRequest
import kotlinx.coroutines.Job
import io.github.magisk317.mipush.common.notification.MockReplayOutcome
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
import java.util.Date
import io.github.magisk317.mipush.manager.SettingsManager

class EventListViewModel constructor(
    private val eventSource: ComparingEventListSource,
    private val eventGateway: ManagerEventGateway,
    private val settingsManager: SettingsManager,
    private val preferenceRepository: PreferenceRepository,
    private val context: Context
) : ViewModel() {
    private val _events = MutableStateFlow<List<EventInfoForDisplay>>(emptyList())
    val events: StateFlow<List<EventInfoForDisplay>> = _events.asStateFlow()

    private val _comparison = MutableStateFlow<EventListComparison?>(null)
    val comparison: StateFlow<EventListComparison?> = _comparison.asStateFlow()
    private var comparisonJob: Job? = null

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

    /**
     * 写入新的保留天数并立即触发一次清理。
     * DataStore 由本方法写入,App 层的 collect 会同步更新 [EventRetentionManager] 的
     * provider 缓存;这里再触发一次即时清理,让改小后的窗口立刻生效。
     */
    fun setEventRetentionDays(days: Int) {
        val coerced = days.coerceAtLeast(1)
        viewModelScope.launch {
            preferenceRepository.setEventRetentionDays(coerced)
            settingsManager.applyEventRetentionDays(coerced)
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
            val primary = withContext(Dispatchers.IO) {
                eventSource.loadPrimary(request)
            }
            val loadedEvents = primary.map { toEventInfoForDisplay(it) }
            if (isRefresh) {
                _events.value = loadedEvents
            } else {
                _events.value = _events.value + loadedEvents
            }
            scheduleComparison(request, primary)
        }
    }

    private fun scheduleComparison(request: EventListRequest, primary: List<ManagerEvent>) {
        comparisonJob?.cancel()
        comparisonJob = viewModelScope.launch {
            _comparison.value = withContext(Dispatchers.IO) {
                eventSource.compareRemote(request, primary)
            }
        }
    }

    override fun onCleared() {
        comparisonJob?.cancel()
        comparisonJob = null
        super.onCleared()
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
            val primary = eventSource.loadPrimary(request)
            scheduleComparison(request, primary)
            primary.map { toEventInfoForDisplay(it) }
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
