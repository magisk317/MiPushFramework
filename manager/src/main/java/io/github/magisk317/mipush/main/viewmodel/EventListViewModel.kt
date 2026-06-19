package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
import io.github.magisk317.mipush.feature.main.subpage.composeKey
import java.util.Date
import io.github.magisk317.mipush.manager.SettingsManager

class EventListViewModel constructor(
    private val eventGateway: ManagerEventGateway,
    private val settingsManager: SettingsManager,
    private val context: Context
) : ViewModel() {
    private val _events = MutableStateFlow<List<EventInfoForDisplay>>(emptyList())
    val events: StateFlow<List<EventInfoForDisplay>> = _events.asStateFlow()

    private val _globalItems = MutableStateFlow<List<EventInfoForDisplay>>(emptyList())
    val globalItems: StateFlow<List<EventInfoForDisplay>> = _globalItems.asStateFlow()

    fun setGlobalItems(items: List<EventInfoForDisplay>) {
        _globalItems.value = items
    }

    fun appendGlobalItemsDistinct(newItems: List<EventInfoForDisplay>) {
        if (newItems.isEmpty()) return
        val current = _globalItems.value
        val existingKeys = current.map { it.composeKey() }.toHashSet()
        val unique = newItems.filter { existingKeys.add(it.composeKey()) }
        if (unique.isNotEmpty()) {
            _globalItems.value = current + unique
        }
    }

    fun removeGlobalItem(item: EventInfoForDisplay) {
        val key = item.composeKey()
        _globalItems.value = _globalItems.value.filter { it.composeKey() != key }
    }

    fun insertGlobalItemAt(index: Int, item: EventInfoForDisplay) {
        val mutable = _globalItems.value.toMutableList()
        mutable.add(index.coerceIn(0, mutable.size), item)
        _globalItems.value = mutable
    }

    fun loadEvents(query: String, packageName: String, isRefresh: Boolean, lastId: Long?) {
        viewModelScope.launch {
            val loadedEvents = withContext(Dispatchers.IO) {
                eventGateway.getEventsById(if (isRefresh) null else lastId, Constants.PAGE_SIZE, packageName, query)
                    .map { 
                        toEventInfoForDisplay(it)
                    }
            }
            if (isRefresh) {
                _events.value = loadedEvents
            } else {
                _events.value = _events.value + loadedEvents
            }
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
    
    fun mockMessage(event: ManagerEvent): Boolean {
        return eventGateway.mockMessage(event)
    }
    
    fun getContent(event: ManagerEvent): String {
        return eventGateway.getContent(event)
    }

    fun getJson(event: ManagerEvent): String? {
        return eventGateway.getJson(event)
    }
    
    suspend fun fetchEventsSuspend(isRefresh: Boolean, lastId: Long?, packageName: String, query: String): List<EventInfoForDisplay> {
        return withContext(Dispatchers.IO) {
            eventGateway.getEventsById(if (isRefresh) null else lastId, Constants.PAGE_SIZE, packageName, query)
                .map { 
                    toEventInfoForDisplay(it)
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
        settingsManager.clearHistory(context, viewModelScope)
    }
}
