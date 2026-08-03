package io.github.magisk317.mipush.manager.runtime.read

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import io.github.magisk317.mipush.common.utils.NotificationUtils
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.notification.NotificationChannelManager
import io.github.magisk317.mipush.notification.NotificationManagerEx
import io.github.magisk317.mipush.notification.RuntimeNotificationChannelNameEnricher

class ManagerNotificationChannelRuntimeReader(
    private val maxPageSize: Int = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
    private val isHookedProvider: () -> Boolean = { NotificationManagerEx.isHooked },
    private val channelProvider: (String) -> List<NotificationChannel?>? =
        NotificationManagerEx::getNotificationChannels,
    private val groupProvider: (String) -> List<NotificationChannelGroup?>? =
        NotificationManagerEx::getNotificationChannelGroups,
    private val channelEnricher: (String, List<NotificationChannel>) -> List<NotificationChannel> =
        RuntimeNotificationChannelNameEnricher::enrich,
) {
    fun readPage(query: ManagerNotificationChannelReadQuery): ManagerNotificationChannelReadPage {
        val pageSize = query.pageSize.coerceIn(1, maxPageSize)
        val packageName = query.packageName
        val isHooked = isHookedProvider()
        val rawChannels = channelEnricher(
            packageName,
            channelProvider(packageName)
                ?.filterNotNull()
                .orEmpty(),
        ).sortedBy { it.id.orEmpty() }
        val rawGroups = groupProvider(packageName)
            ?.filterNotNull()
            .orEmpty()
            // HyperOS adds a null-ID container for ungrouped channels; it is not a real group.
            .mapNotNull { group ->
                group.id?.takeIf(String::isNotBlank)?.let { groupId -> group to groupId }
            }
            .sortedBy { (_, groupId) -> groupId }

        val startAfter = query.pageToken
            ?.takeIf { it.isNotBlank() }
            ?.let { ManagerNotificationChannelPageToken.decode(packageName, it) }

        val remaining = if (startAfter == null) {
            rawChannels
        } else {
            rawChannels.dropWhile { (it.id ?: "") <= startAfter }
        }
        val pageItems = remaining.take(pageSize)
        val nextToken = if (remaining.size > pageSize) {
            val lastId = pageItems.lastOrNull()?.id.orEmpty()
            if (lastId.isNotEmpty()) {
                ManagerNotificationChannelPageToken.encode(packageName, lastId)
            } else {
                null
            }
        } else {
            null
        }

        // Groups are small metadata; always return the full group list for the package so the
        // manager can rebuild sections without a second capability.
        return ManagerNotificationChannelReadPage(
            packageName = packageName,
            isHooked = isHooked,
            items = pageItems.map { it.toReadSummary(packageName) },
            groups = rawGroups.map { (group, groupId) ->
                group.toGroupSummary(packageName, groupId)
            },
            nextPageToken = nextToken,
        )
    }

    private fun NotificationChannel.toReadSummary(packageName: String): ManagerNotificationChannelReadSummary {
        val channelId = id.orEmpty()
        return ManagerNotificationChannelReadSummary(
            id = channelId,
            name = name?.toString().orEmpty(),
            importance = importance,
            groupId = group,
            description = description,
            enabled = NotificationChannelManager.isNotificationChannelEnabled(this),
            managedByMiPush = NotificationUtils.isMiPushManagedChannelId(packageName, channelId) ||
                NotificationUtils.isMiPushManagedGroupId(packageName, group),
        )
    }

    private fun NotificationChannelGroup.toGroupSummary(
        packageName: String,
        groupId: String,
    ): ManagerNotificationChannelGroupReadSummary {
        return ManagerNotificationChannelGroupReadSummary(
            id = groupId,
            name = name?.toString().orEmpty(),
            managedByMiPush = NotificationUtils.isMiPushManagedGroupId(packageName, groupId),
        )
    }
}
