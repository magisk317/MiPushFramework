package io.github.magisk317.mipush.manager.runtime.read

data class ManagerNotificationChannelReadQuery(
    val packageName: String,
    val pageSize: Int = 100,
    val pageToken: String? = null,
    val userId: Int = -1,
)

data class ManagerNotificationChannelReadSummary(
    val id: String,
    val name: String,
    val importance: Int,
    val groupId: String?,
    val description: String?,
    val enabled: Boolean,
    val managedByMiPush: Boolean,
)

data class ManagerNotificationChannelGroupReadSummary(
    val id: String,
    val name: String,
    val managedByMiPush: Boolean,
)

data class ManagerNotificationChannelReadPage(
    val packageName: String,
    val isHooked: Boolean,
    val userId: Int,
    val items: List<ManagerNotificationChannelReadSummary>,
    val groups: List<ManagerNotificationChannelGroupReadSummary>,
    val nextPageToken: String?,
)
