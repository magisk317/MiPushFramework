package io.github.magisk317.mipush.manager.notification

import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelPageDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelQueryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import io.github.magisk317.mipush.common.utils.Utils
import kotlinx.coroutines.CancellationException

data class NotificationChannelSnapshot(
    val packageName: String,
    val isHooked: Boolean,
    val channels: List<NotificationChannelSummary>,
    val groups: List<NotificationChannelGroupSummary>,
)

data class NotificationChannelSummary(
    val id: String,
    val name: String,
    val importance: Int,
    val groupId: String?,
    val description: String?,
    val enabled: Boolean,
    val managedByMiPush: Boolean,
)

data class NotificationChannelGroupSummary(
    val id: String,
    val name: String,
    val managedByMiPush: Boolean,
)

sealed interface NotificationChannelReadResult<out T> {
    data class Available<T>(val value: T) : NotificationChannelReadResult<T>
    data class Unavailable(val status: NotificationChannelReadStatus) : NotificationChannelReadResult<Nothing>
}

enum class NotificationChannelReadStatus {
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

class RemoteNotificationChannelSource internal constructor(
    private val pageLoader: suspend (ManagerNotificationChannelQueryDto) ->
    ManagerRuntimeResult<ManagerNotificationChannelPageDto>,
    private val pageSizeProvider: () -> Int,
    private val userIdProvider: () -> Int = { Utils.myUserId() },
) {
    constructor(client: ManagerRuntimeClient) : this(
        pageLoader = client::getNotificationChannelPage,
        pageSizeProvider = {
            val negotiated = (client.availability.value as? ManagerRuntimeAvailability.Available)
                ?.handshake
                ?.maxPageSize
                ?: ManagerProtocol.DEFAULT_MAX_PAGE_SIZE
            minOf(negotiated, ManagerProtocol.DEFAULT_MAX_PAGE_SIZE)
        },
    )

    suspend fun load(packageName: String): NotificationChannelReadResult<NotificationChannelSnapshot> = try {
        val pageSize = pageSizeProvider().coerceAtLeast(1)
        val userId = userIdProvider().coerceAtLeast(0)
        val items = mutableListOf<NotificationChannelSummary>()
        val seenTokens = mutableSetOf<String>()
        var token: String? = null
        var isHooked = false
        var groups = emptyList<NotificationChannelGroupSummary>()
        var pageCount = 0
        do {
            if (++pageCount > MAX_PAGE_REQUESTS) {
                return NotificationChannelReadResult.Unavailable(NotificationChannelReadStatus.FAILED)
            }
            when (
                val result = pageLoader(
                    ManagerNotificationChannelQueryDto(
                        packageName = packageName,
                        pageSize = pageSize,
                        pageToken = token,
                        userId = userId,
                    ),
                )
            ) {
                is ManagerRuntimeResult.Success -> {
                    val page = result.value
                    if (page.userId != userId) {
                        return NotificationChannelReadResult.Unavailable(NotificationChannelReadStatus.FAILED)
                    }
                    isHooked = page.isHooked
                    groups = page.groups.map {
                        NotificationChannelGroupSummary(
                            id = it.id,
                            name = it.name,
                            managedByMiPush = it.managedByMiPush,
                        )
                    }.sortedBy { it.id }
                    items += page.items.map {
                        NotificationChannelSummary(
                            id = it.id,
                            name = it.name,
                            importance = it.importance,
                            groupId = it.groupId,
                            description = it.description,
                            enabled = it.enabled,
                            managedByMiPush = it.managedByMiPush,
                        )
                    }
                    val next = page.nextPageToken
                    if (next != null && !seenTokens.add(next)) {
                        return NotificationChannelReadResult.Unavailable(NotificationChannelReadStatus.FAILED)
                    }
                    token = next
                }
                is ManagerRuntimeResult.Unsupported ->
                    return NotificationChannelReadResult.Unavailable(NotificationChannelReadStatus.UNSUPPORTED)
                is ManagerRuntimeResult.Unavailable ->
                    return NotificationChannelReadResult.Unavailable(
                        result.availability.toNotificationChannelReadStatus(),
                    )
                is ManagerRuntimeResult.Failed ->
                    return NotificationChannelReadResult.Unavailable(NotificationChannelReadStatus.FAILED)
            }
        } while (token != null)

        NotificationChannelReadResult.Available(
            NotificationChannelSnapshot(
                packageName = packageName,
                isHooked = isHooked,
                channels = items.sortedBy { it.id },
                groups = groups,
            ),
        )
    } catch (error: CancellationException) {
        throw error
    } catch (_: RuntimeException) {
        NotificationChannelReadResult.Unavailable(NotificationChannelReadStatus.FAILED)
    }

    private companion object {
        private const val MAX_PAGE_REQUESTS = 64
    }
}

private fun ManagerRuntimeAvailability.toNotificationChannelReadStatus(): NotificationChannelReadStatus =
    when (this) {
        is ManagerRuntimeAvailability.Disconnected -> NotificationChannelReadStatus.DISCONNECTED
        is ManagerRuntimeAvailability.Binding -> NotificationChannelReadStatus.BINDING
        is ManagerRuntimeAvailability.RuntimeMissing -> NotificationChannelReadStatus.RUNTIME_MISSING
        is ManagerRuntimeAvailability.PermissionDenied -> NotificationChannelReadStatus.PERMISSION_DENIED
        is ManagerRuntimeAvailability.TimedOut -> NotificationChannelReadStatus.TIMED_OUT
        is ManagerRuntimeAvailability.Incompatible -> NotificationChannelReadStatus.INCOMPATIBLE
        is ManagerRuntimeAvailability.TemporarilyDisconnected ->
            NotificationChannelReadStatus.TEMPORARILY_DISCONNECTED
        is ManagerRuntimeAvailability.Failed -> NotificationChannelReadStatus.FAILED
        is ManagerRuntimeAvailability.Available -> NotificationChannelReadStatus.FAILED
    }
