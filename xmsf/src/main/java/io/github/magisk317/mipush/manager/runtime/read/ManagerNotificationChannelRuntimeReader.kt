package io.github.magisk317.mipush.manager.runtime.read

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import io.github.magisk317.mipush.common.utils.NotificationUtils
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.notification.NotificationChannelManager
import io.github.magisk317.mipush.notification.NotificationManagerEx

class ManagerNotificationChannelRuntimeReader(
    private val maxPageSize: Int = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
) {
    fun readPage(query: ManagerNotificationChannelReadQuery): ManagerNotificationChannelReadPage {
        val pageSize = query.pageSize.coerceIn(1, maxPageSize)
        val packageName = query.packageName
        val isHooked = NotificationManagerEx.isHooked
        val rawChannels = NotificationManagerEx.getNotificationChannels(packageName)
            ?.filterNotNull()
            .orEmpty()
            .sortedBy { it.id.orEmpty() }
        val rawGroups = NotificationManagerEx.getNotificationChannelGroups(packageName)
            ?.filterNotNull()
            .orEmpty()
            .sortedBy { it.id.orEmpty() }

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
            groups = rawGroups.map { it.toGroupSummary(packageName) },
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
    ): ManagerNotificationChannelGroupReadSummary {
        val groupId = id.orEmpty()
        return ManagerNotificationChannelGroupReadSummary(
            id = groupId,
            name = name?.toString().orEmpty(),
            managedByMiPush = NotificationUtils.isMiPushManagedGroupId(packageName, groupId),
        )
    }
}
