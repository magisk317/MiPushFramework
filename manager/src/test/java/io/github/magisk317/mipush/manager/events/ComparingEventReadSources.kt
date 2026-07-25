package io.github.magisk317.mipush.manager.events

import io.github.magisk317.mipush.common.manager.ManagerEvent

sealed interface EventListComparison {
    data object Matched : EventListComparison
    data class Mismatched(val fields: List<String>) : EventListComparison
    data class Unavailable(val status: EventReadStatus) : EventListComparison
}

class ComparingEventListSource(
    private val primarySource: GatewayEventListSource,
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

