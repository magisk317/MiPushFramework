package io.github.magisk317.mipush.manager.events

import io.github.magisk317.mipush.common.manager.ManagerDayCount
import io.github.magisk317.mipush.common.manager.ManagerEvent
import io.github.magisk317.mipush.common.manager.ManagerEventGateway
import io.github.magisk317.mipush.common.notification.MockReplayOutcome
import io.github.magisk317.mipush.manager.api.ManagerEventPageDto
import io.github.magisk317.mipush.manager.api.ManagerEventSummaryDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventReadSourcesTest {
    @Test
    fun `remote event source maps success pages`() = runBlocking {
        val source = RemoteEventListSource {
            ManagerRuntimeResult.Success(
                ManagerEventPageDto(
                    items = listOf(
                        ManagerEventSummaryDto(
                            id = 9L,
                            packageName = "com.example",
                            title = "t",
                            content = "c",
                            configOptions = listOf("disable"),
                        ),
                    ),
                ),
            )
        }
        val result = source.load(EventListRequest(pageSize = 20))
        assertTrue(result is EventReadResult.Available)
        val events = (result as EventReadResult.Available).value
        assertEquals(1, events.size)
        assertEquals(9L, events.single().id)
        assertEquals(setOf("disable"), events.single().configOptions)
    }

    @Test
    fun `comparison reports field names without values`() = runBlocking {
        val primary = listOf(
            ManagerEvent(
                id = 1L,
                packageName = "com.example",
                configOptions = emptySet(),
                channel = "a",
                receiveDateMs = 1L,
                title = "t1",
                content = "c1",
            ),
        )
        val remote = RemoteEventListSource {
            ManagerRuntimeResult.Success(
                ManagerEventPageDto(
                    items = listOf(
                        ManagerEventSummaryDto(
                            id = 1L,
                            packageName = "com.example",
                            channel = "b",
                            receiveDateMs = 1L,
                            title = "t2",
                            content = "c1",
                        ),
                    ),
                ),
            )
        }
        val comparing = ComparingEventListSource(
            primarySource = GatewayEventListSource(FakeEventGateway(primary)),
            remoteSource = remote,
            enableRemoteCompare = true,
        )
        val comparison = comparing.compareRemote(EventListRequest(), primary)
        assertTrue(comparison is EventListComparison.Mismatched)
        val fields = (comparison as EventListComparison.Mismatched).fields
        assertTrue(fields.contains("channel"))
        assertTrue(fields.contains("title"))
        assertTrue(fields.none { it.contains("t1") || it.contains("t2") })
    }

    private class FakeEventGateway(
        private val events: List<ManagerEvent>,
    ) : ManagerEventGateway {
        override fun getEventsById(lastId: Long?, size: Int, packageName: String, query: String) = events
        override fun startManagePermissions(packageName: String, ignoreNotRegistered: Boolean) = Unit
        override suspend fun startConfigPreview(packageName: String) = Unit
        override fun copyToClipboard(content: String) = Unit
        override suspend fun mockMessage(event: ManagerEvent): MockReplayOutcome = MockReplayOutcome.Failed
        override fun getJson(event: ManagerEvent): String? = null
        override fun getContent(event: ManagerEvent): String = event.content
        override suspend fun deleteEvent(event: ManagerEvent): Boolean = false
        override suspend fun restoreEvent(event: ManagerEvent): ManagerEvent? = null
        override suspend fun countEventsByDay(): List<ManagerDayCount> = emptyList()
        override suspend fun clearHistoryBefore(cutoffMillis: Long): Int = 0
        override suspend fun clearHistoryInRange(startMillis: Long, endMillis: Long): Int = 0
    }
}
