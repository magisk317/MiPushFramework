package io.github.magisk317.mipush.main.viewmodel

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.github.magisk317.mipush.Global
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.data.EventRepository
import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
import java.util.Date
import javax.inject.Inject
import io.github.magisk317.mipush.runtime.store.event.type.TypeFactory
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.app.SettingsManager

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

    fun startConfigPreview(packageName: String) {
        viewModelScope.launch {
            eventRepository.startConfigPreview(packageName)
        }
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
