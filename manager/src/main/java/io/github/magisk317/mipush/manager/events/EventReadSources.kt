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

sealed interface EventListComparison {
    data object Matched : EventListComparison
    data class Mismatched(val fields: List<String>) : EventListComparison
    data class Unavailable(val status: EventReadStatus) : EventListComparison
}

class InProcessEventListSource(
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

class ComparingEventListSource(
    private val primarySource: InProcessEventListSource,
    private val remoteSource: RemoteEventListSource,
    private val enableRemoteCompare: Boolean = false,
) {
    fun loadPrimary(request: EventListRequest): List<ManagerEvent> = primarySource.load(request)

    suspend fun compareRemote(
        request: EventListRequest,
        primary: List<ManagerEvent>,
    ): EventListComparison {
        if (!enableRemoteCompare) return EventListComparison.Matched
        return when (val remote = remoteSource.load(request)) {
            is EventReadResult.Available -> compareEventLists(primary, remote.value)
            is EventReadResult.Unavailable -> EventListComparison.Unavailable(remote.status)
        }
    }
}

private fun compareEventLists(
    primary: List<ManagerEvent>,
    remote: List<ManagerEvent>,
): EventListComparison {
    val fields = mutableListOf<String>()
    if (primary.size != remote.size) fields += "size"
    val primaryById = primary.associateBy { it.id }
    val remoteById = remote.associateBy { it.id }
    if (primaryById.keys != remoteById.keys) fields += "ids"
    primaryById.forEach { (id, left) ->
        val right = remoteById[id] ?: return@forEach
        if (left.packageName != right.packageName) fields += "packageName"
        if (left.type != right.type) fields += "type"
        if (left.result != right.result) fields += "result"
        if (left.receiveDateMs != right.receiveDateMs) fields += "receiveDateMs"
        if (left.title != right.title) fields += "title"
        if (left.content != right.content) fields += "content"
        if (left.channel != right.channel) fields += "channel"
        if (left.appName != right.appName) fields += "appName"
        if (left.configOptions != right.configOptions) fields += "configOptions"
        if (left.info != right.info) fields += "info"
        if (!left.payload.contentEquals(right.payload)) fields += "payload"
        if (left.regSec != right.regSec) fields += "regSec"
    }
    return if (fields.isEmpty()) {
        EventListComparison.Matched
    } else {
        EventListComparison.Mismatched(fields.distinct().sorted())
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
