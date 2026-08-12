package io.github.magisk317.mipush.manager.notification

import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelGroupSummaryDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelPageDto
import io.github.magisk317.mipush.manager.api.ManagerNotificationChannelSummaryDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteNotificationChannelSourceTest {
    @Test
    fun `paged DTOs map to one sorted domain snapshot without framework reconstruction`() = runBlocking {
        val queries = mutableListOf<Pair<Int, String?>>()
        val group = ManagerNotificationChannelGroupSummaryDto(
            id = "group",
            name = "Group",
            managedByMiPush = true,
        )
        val source = RemoteNotificationChannelSource(
            pageLoader = { query ->
                queries += query.pageSize to query.pageToken
                ManagerRuntimeResult.Success(
                    when (query.pageToken) {
                        null -> ManagerNotificationChannelPageDto(
                            packageName = query.packageName,
                            isHooked = true,
                            items = listOf(
                                ManagerNotificationChannelSummaryDto(
                                    id = "channel-b",
                                    name = "B",
                                    importance = 4,
                                    groupId = group.id,
                                    description = "details",
                                    enabled = false,
                                    managedByMiPush = true,
                                ),
                            ),
                            groups = listOf(group),
                            nextPageToken = "next",
                        )
                        else -> ManagerNotificationChannelPageDto(
                            packageName = query.packageName,
                            isHooked = true,
                            items = listOf(
                                ManagerNotificationChannelSummaryDto(
                                    id = "channel-a",
                                    name = "A",
                                    importance = 2,
                                    enabled = true,
                                    managedByMiPush = false,
                                ),
                            ),
                            groups = listOf(group),
                        )
                    },
                )
            },
            pageSizeProvider = { 1 },
        )

        val result = source.load("com.example") as NotificationChannelReadResult.Available
        val snapshot = result.value

        assertEquals(listOf(1 to null, 1 to "next"), queries)
        assertEquals("com.example", snapshot.packageName)
        assertEquals(true, snapshot.isHooked)
        assertEquals(listOf("channel-a", "channel-b"), snapshot.channels.map { it.id })
        assertEquals(false, snapshot.channels.single { it.id == "channel-b" }.enabled)
        assertEquals(true, snapshot.channels.single { it.id == "channel-b" }.managedByMiPush)
        assertEquals(listOf("group"), snapshot.groups.map { it.id })
    }

    @Test
    fun `runtime availability maps to typed read status`() = runBlocking {
        val source = RemoteNotificationChannelSource(
            pageLoader = {
                ManagerRuntimeResult.Unavailable(ManagerRuntimeAvailability.PermissionDenied)
            },
            pageSizeProvider = { 20 },
        )

        val result = source.load("com.example") as NotificationChannelReadResult.Unavailable

        assertEquals(NotificationChannelReadStatus.PERMISSION_DENIED, result.status)
    }

    @Test
    fun `mismatched runtime user remains unavailable`() = runBlocking {
        val source = RemoteNotificationChannelSource(
            pageLoader = {
                ManagerRuntimeResult.Success(
                    ManagerNotificationChannelPageDto(
                        packageName = "com.example",
                        userId = 999,
                    ),
                )
            },
            pageSizeProvider = { 20 },
            userIdProvider = { 0 },
        )

        val result = source.load("com.example") as NotificationChannelReadResult.Unavailable

        assertTrue(result.status == NotificationChannelReadStatus.FAILED)
    }
}
