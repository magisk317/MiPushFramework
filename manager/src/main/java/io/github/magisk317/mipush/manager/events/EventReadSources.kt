package io.github.magisk317.mipush.manager.events

import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.manager.api.ManagerEventPageDto
import io.github.magisk317.mipush.manager.api.ManagerEventQueryDto
import io.github.magisk317.mipush.manager.api.ManagerEventSummaryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import io.github.magisk317.mipush.common.utils.logW
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
    fun load(request: EventListRequest): List<ManagerEvent> =
        eventGateway.getEventsById(
            lastId = request.lastId,
            size = request.pageSize,
            packageName = request.packageName,
            query = request.query,
        )
}

class RemoteEventListSource internal constructor(
    private val pageLoader: suspend (ManagerEventQueryDto) -> ManagerRuntimeResult<ManagerEventPageDto>,
) {
    constructor(client: ManagerRuntimeClient) : this(client::getEventPage)

    suspend fun load(request: EventListRequest): EventReadResult<List<ManagerEvent>> = try {
        when (
            val result = pageLoader(
                ManagerEventQueryDto(
                    lastId = request.lastId,
                    pageSize = request.pageSize,
                    packageName = request.packageName,
                    query = request.query,
                ),
            )
        ) {
            is ManagerRuntimeResult.Success -> EventReadResult.Available(
                result.value.items.map { it.toManagerEvent() },
            )
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
}

private fun ManagerEventSummaryDto.toManagerEvent(): ManagerEvent =
    ManagerEvent(
        id = id,
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
