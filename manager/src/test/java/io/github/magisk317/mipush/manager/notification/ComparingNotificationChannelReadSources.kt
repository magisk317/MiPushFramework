package io.github.magisk317.mipush.manager.notification

sealed interface NotificationChannelComparison {
    data object Matched : NotificationChannelComparison
    data class Mismatched(val fields: List<String>) : NotificationChannelComparison
    data class Unavailable(val status: NotificationChannelReadStatus) : NotificationChannelComparison
}

class ComparingNotificationChannelSource(
    private val primarySource: GatewayNotificationChannelSource,
    private val remoteSource: RemoteNotificationChannelSource,
    private val enableRemoteCompare: Boolean = false,
) {
    fun loadPrimary(packageName: String): NotificationChannelSnapshot = primarySource.load(packageName)

    suspend fun compareRemote(
        packageName: String,
        primary: NotificationChannelSnapshot,
    ): NotificationChannelComparison {
        if (!enableRemoteCompare) return NotificationChannelComparison.Matched
        return when (val remote = remoteSource.load(packageName)) {
            is NotificationChannelReadResult.Available -> compareSnapshots(primary, remote.value)
            is NotificationChannelReadResult.Unavailable ->
                NotificationChannelComparison.Unavailable(remote.status)
        }
    }
}

private fun compareSnapshots(
    primary: NotificationChannelSnapshot,
    remote: NotificationChannelSnapshot,
): NotificationChannelComparison {
    val fields = mutableListOf<String>()
    if (primary.isHooked != remote.isHooked) fields += "isHooked"
    if (primary.channels.map { it.id } != remote.channels.map { it.id }) fields += "channelIds"
    if (primary.groups.map { it.id } != remote.groups.map { it.id }) fields += "groupIds"
    val primaryChannels = primary.channels.associateBy { it.id }
    remote.channels.forEach { right ->
        val left = primaryChannels[right.id] ?: return@forEach
        if (left.name != right.name) fields += "name"
        if (left.importance != right.importance) fields += "importance"
        if (left.groupId != right.groupId) fields += "groupId"
        if (left.enabled != right.enabled) fields += "enabled"
        if (left.managedByMiPush != right.managedByMiPush) fields += "managedByMiPush"
    }
    return if (fields.isEmpty()) {
        NotificationChannelComparison.Matched
    } else {
        NotificationChannelComparison.Mismatched(fields.distinct().sorted())
    }
}

