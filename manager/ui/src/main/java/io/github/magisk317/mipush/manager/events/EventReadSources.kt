package io.github.magisk317.mipush.manager.events

import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.ManagerEventGateway
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.manager.api.ManagerEventPageDto
import io.github.magisk317.mipush.manager.api.ManagerEventQueryDto
import io.github.magisk317.mipush.manager.api.ManagerEventSummaryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import io.github.magisk317.mipush.manager.client.RemoteCallBudget
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.mipush.manager.remote.PageRemoteCallAdapter
import io.github.magisk317.mipush.manager.remote.PageRemoteCallPolicy
import kotlinx.coroutines.CancellationException

data class EventListRequest(
    val lastId: Long? = null,
    val pageSize: Int = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
    val packageName: String = "",
    val query: String = "",
)

sealed interface EventReadResult<out T> {
    data class Available<T>(val value: T) : EventReadResult<T>
    data class Unavailable(val status: EventReadStatus) : EventReadResult<Nothing>
}

enum class EventReadStatus {
    UNSUPPORTED,
    DISCONNECTED,
    BINDING,
    RUNTIME_MISSING,
    PERMISSION_DENIED,
    TIMED_OUT,
    INCOMPATIBLE,
    TEMPORARILY_DISCONNECTED,
    FAILED,
}

class GatewayEventListSource(
    private val eventGateway: ManagerEventGateway,
) {
    suspend fun load(request: EventListRequest): List<ManagerEvent> =
        eventGateway.getEventsById(
            lastId = request.lastId,
            size = request.pageSize,
            packageName = request.packageName,
            query = request.query,
        )
}

class RemoteEventListSource internal constructor(
    private val pageLoader: suspend (ManagerEventQueryDto) -> ManagerRuntimeResult<ManagerEventPageDto>,
    private val userIdProvider: () -> Int = { Utils.myUserId() },
    private val pageCallAdapter: PageRemoteCallAdapter? = null,
) {
    constructor(client: ManagerRuntimeClient, pageCallAdapter: PageRemoteCallAdapter? = null) : this(
        pageLoader = client::getEventPage,
        pageCallAdapter = pageCallAdapter,
    )

    suspend fun load(
        request: EventListRequest,
        budget: RemoteCallBudget = PageRemoteCallPolicy.visiblePage,
    ): EventReadResult<List<ManagerEvent>> = try {
        val userId = userIdProvider().coerceAtLeast(0)
        when (
            val result = loadPage(
                ManagerEventQueryDto(
                    lastId = request.lastId,
                    pageSize = request.pageSize,
                    packageName = request.packageName,
                    query = request.query,
                    userId = userId,
                ),
                budget,
            )
        ) {
            is ManagerRuntimeResult.Success -> {
                if (result.value.items.any { it.userId != userId }) {
                    EventReadResult.Unavailable(EventReadStatus.FAILED)
                } else {
                    EventReadResult.Available(result.value.items.map { it.toManagerEvent() })
                }
            }
            is ManagerRuntimeResult.Unsupported -> EventReadResult.Unavailable(EventReadStatus.UNSUPPORTED)
            is ManagerRuntimeResult.Unavailable -> {
                logW("RemoteEventListSource unavailable availability=${result.availability}")
                EventReadResult.Unavailable(result.availability.toEventReadStatus())
            }
            is ManagerRuntimeResult.Failed -> EventReadResult.Unavailable(EventReadStatus.FAILED)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: RuntimeException) {
        EventReadResult.Unavailable(EventReadStatus.FAILED)
    }

    private suspend fun loadPage(
        query: ManagerEventQueryDto,
        budget: RemoteCallBudget,
    ): ManagerRuntimeResult<ManagerEventPageDto> {
        val adapter = pageCallAdapter ?: return pageLoader(query)
        return when (val scheduled = adapter.call(
            operation = "event_page",
            budget = budget,
        ) { pageLoader(query) }) {
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Success -> scheduled.value
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Unavailable ->
                ManagerRuntimeResult.Unavailable(scheduled.availability)
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Busy ->
                ManagerRuntimeResult.Failed("runtime_busy")
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Timeout ->
                ManagerRuntimeResult.Failed("runtime_request_timeout")
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Cancelled,
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Stale,
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.ValidationFailed,
            -> ManagerRuntimeResult.Failed("stale_or_cancelled")
        }
    }
}

private fun ManagerEventSummaryDto.toManagerEvent(): ManagerEvent =
    ManagerEvent(
        id = id,
        userId = userId,
        packageName = packageName,
        configOptions = configOptions.toSet(),
        channel = channel,
        receiveDateMs = receiveDateMs,
        title = title,
        content = content,
        appName = appName,
        type = type,
        result = result,
        info = info,
        payload = payload,
        regSec = regSec,
    )

private fun ManagerRuntimeAvailability.toEventReadStatus(): EventReadStatus = when (this) {
    is ManagerRuntimeAvailability.Disconnected -> EventReadStatus.DISCONNECTED
    is ManagerRuntimeAvailability.Binding -> EventReadStatus.BINDING
    is ManagerRuntimeAvailability.RuntimeMissing -> EventReadStatus.RUNTIME_MISSING
    is ManagerRuntimeAvailability.PermissionDenied -> EventReadStatus.PERMISSION_DENIED
    is ManagerRuntimeAvailability.TimedOut -> EventReadStatus.TIMED_OUT
    is ManagerRuntimeAvailability.Incompatible -> EventReadStatus.INCOMPATIBLE
    is ManagerRuntimeAvailability.TemporarilyDisconnected -> EventReadStatus.TEMPORARILY_DISCONNECTED
    is ManagerRuntimeAvailability.Failed -> EventReadStatus.FAILED
    is ManagerRuntimeAvailability.Available -> EventReadStatus.FAILED
}
