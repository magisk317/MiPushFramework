package com.magisk317.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.magisk317.Global
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.trumeet.common.Constants
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipushframework.data.EventRepository
import top.trumeet.mipushframework.main.subpage.EventInfoForDisplay
import java.util.Date
import javax.inject.Inject
import top.trumeet.mipush.provider.event.type.TypeFactory
import com.xiaomi.xmsf.push.utils.RegSecUtils
import com.xiaomi.xmsf.SettingsManager

@HiltViewModel
class EventListViewModel @Inject constructor(
    private val eventRepository: EventRepository,
    private val settingsManager: SettingsManager,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private val _events = MutableStateFlow<List<EventInfoForDisplay>>(emptyList())
    val events: StateFlow<List<EventInfoForDisplay>> = _events.asStateFlow()

    fun loadEvents(query: String, packageName: String, isRefresh: Boolean, lastId: Long?) {
        viewModelScope.launch {
            val loadedEvents = withContext(Dispatchers.IO) {
                eventRepository.getEventsById(if (isRefresh) null else lastId, Constants.PAGE_SIZE, packageName, query)
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

    private fun toEventInfoForDisplay(it: Event): EventInfoForDisplay {
        val type = TypeFactory.createForDisplay(it)
        val container = RegSecUtils.getContainerWithRegSec(it)
        
        val summary = type.getSummary(context).toString()
        val content = if (container != null)
            eventRepository.getDecoratedSummary(
                summary,
                container
            )
        else summary
        
        return EventInfoForDisplay(
            id = it.id ?: 0L,
            packageName = it.pkg,
            configOptions = eventRepository.getStatus(container),
            channel = eventRepository.getStatusDescription(it),
            receiveDate = Date(it.date),
            title = type.getTitle(context).toString(),
            content = content,
            appName = Global.ApplicationNameCache().getAppName(context, it.pkg).toString(),
            event = it,
        )
    }

    fun startManagePermissions(packageName: String) {
        eventRepository.startManagePermissions(packageName)
    }
    
    fun copyToClipboard(content: String) {
        eventRepository.copyToClipboard(content)
    }
    
    fun mockMessage(container: XmPushActionContainer) {
        eventRepository.mockMessage(container)
    }
    
    fun getContent(event: Event, container: XmPushActionContainer): String {
        return eventRepository.getContent(event, container)
    }

    fun getJson(event: Event): String {
        return eventRepository.getJson(event).toString()
    }
    
    suspend fun fetchEventsSuspend(isRefresh: Boolean, lastId: Long?, packageName: String, query: String): List<EventInfoForDisplay> {
        return withContext(Dispatchers.IO) {
            eventRepository.getEventsById(if (isRefresh) null else lastId, Constants.PAGE_SIZE, packageName, query)
                .map { 
                    toEventInfoForDisplay(it)
                }
        }
    }

    fun clearHistory() {
        settingsManager.clearHistory(context, viewModelScope)
    }
}
