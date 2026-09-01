package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.ManagerEventResult
import io.github.magisk317.mipush.manager.application.ManagerEventType
import io.github.magisk317.mipush.manager.events.EventReadResult
import io.github.magisk317.mipush.manager.events.EventReadStatus
import io.github.magisk317.mipush.manager.remote.RuntimeReadUnavailableException
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EventListViewModelTest {
    @Test
    fun `non-zero refresh signal bypasses persisted event cache`() {
        assertTrue(EventListViewModel.shouldUseEventCache(refreshSignal = 0))
        assertFalse(EventListViewModel.shouldUseEventCache(refreshSignal = 1))
    }

    @Test
    fun `presentation refresh signal changes restore identity without changing cache scope`() {
        val initial = EventListViewModel.eventListRestoreKey("", "", refreshSignal = 0)
        val refreshed = EventListViewModel.eventListRestoreKey("", "", refreshSignal = 1)

        assertFalse(initial == refreshed)
        assertTrue(initial.startsWith("q=;p=;refresh="))
        assertTrue(refreshed.endsWith("1"))
    }

    @Test
    fun `default cached visibility matches remote event query policy`() {
        val events = listOf(
            displayEvent(1, ManagerEventType.REGISTRATION_RESULT, ManagerEventResult.OK, 100L),
            displayEvent(2, ManagerEventType.REGISTRATION_RESULT, ManagerEventResult.OK, 200L),
            displayEvent(3, ManagerEventType.REGISTRATION, ManagerEventResult.OK, 300L),
            displayEvent(4, ManagerEventType.NOTIFICATION, ManagerEventResult.OK, 400L),
            displayEvent(5, ManagerEventType.SEND_MESSAGE, ManagerEventResult.DENY_DISABLED, 500L),
            displayEvent(6, ManagerEventType.SEND_MESSAGE, ManagerEventResult.OK, 600L),
            displayEvent(
                7,
                ManagerEventType.SEND_MESSAGE,
                ManagerEventResult.OK,
                700L,
                configOptions = setOf("disable"),
            ),
        )

        val visible = EventListViewModel.filterCachedEventsForDefaultMode(events)

        assertEquals(listOf(1L, 6L), visible.map { it.id })
    }

    @Test
    fun `legacy cached registration content is not treated as a send message`() {
        val events = listOf(
            legacyEvent(1L, "尝试注册推送", 100L),
            legacyEvent(2L, "收到注册结果", 200L),
            legacyEvent(3L, "收到注册结果", 300L),
        )

        val visible = EventListViewModel.filterCachedEventsForDefaultMode(events)

        assertEquals(listOf(2L), visible.map { it.id })
    }
    @Test
    fun `default cached visibility keeps first successful registration result per app`() {
        val events = listOf(
            displayEvent(1, ManagerEventType.REGISTRATION_RESULT, ManagerEventResult.OK, 100L),
            displayEvent(2, ManagerEventType.REGISTRATION_RESULT, ManagerEventResult.OK, 200L),
            displayEvent(3, ManagerEventType.SEND_MESSAGE, ManagerEventResult.OK, 300L),
        )

        val visible = EventListViewModel.filterCachedEventsForDefaultMode(events)

        assertEquals(listOf(1L, 3L), visible.map { it.id })
    }

    @Test
    fun `legacy cached notification and command summaries are not treated as send messages`() {
        val events = listOf(
            legacyEvent(1L, "收到通知: normal_client_config_update", 100L),
            legacyEvent(2L, "收到命令: set-alias", 200L),
            legacyEvent(3L, "Receive notification: registration id expired", 300L),
            legacyEvent(4L, "Receive command: set-alias", 400L),
        )

        val visible = EventListViewModel.filterCachedEventsForDefaultMode(events)

        assertEquals(emptyList<Long>(), visible.map { it.id })
    }

    @Test
    fun `show all cached visibility preserves every event`() {
        val events = listOf(
            displayEvent(2, ManagerEventType.REGISTRATION, ManagerEventResult.OK),
            displayEvent(3, ManagerEventType.SEND_MESSAGE, ManagerEventResult.DENY_DISABLED),
        )

        assertEquals(events, EventListViewModel.eventsForDisplay(events, showAllEvents = true))
    }
    @Test
    fun `unavailable event reads remain failures instead of becoming empty data`() {
        val error = runCatching {
            EventReadResult.Unavailable(EventReadStatus.DISCONNECTED)
                .requireAvailableEvents("loadEvents")
        }.exceptionOrNull()

        assertTrue(error is RuntimeReadUnavailableException)
        assertEquals("DISCONNECTED", (error as RuntimeReadUnavailableException).status)
        assertEquals("loadEvents", error.operation)
    }

    @Test
    fun `snapshot cursor follows the last retained event when the list is truncated`() {
        val events = (1L..250L).map(::event)

        val snapshot = EventListViewModel.buildEventListSnapshot(
            events = events,
            lastId = 250L,
            hasMore = false,
        )

        assertEquals(EventListViewModel.MAX_EVENT_LIST_SNAPSHOT_EVENTS, snapshot.events.size)
        assertEquals(200L, snapshot.events.last().id)
        assertEquals(200L, snapshot.lastId)
        assertTrue(snapshot.hasMore)
    }

    @Test
    fun `snapshot preserves cursor and hasMore when no truncation is needed`() {
        val events = (1L..3L).map(::event)

        val snapshot = EventListViewModel.buildEventListSnapshot(
            events = events,
            lastId = 3L,
            hasMore = false,
        )

        assertEquals(events, snapshot.events)
        assertEquals(3L, snapshot.lastId)
        assertFalse(snapshot.hasMore)
    }

    @Test
    fun `snapshot retains an existing hasMore signal`() {
        val events = (1L..3L).map(::event)

        val snapshot = EventListViewModel.buildEventListSnapshot(
            events = events,
            lastId = 3L,
            hasMore = true,
        )

        assertTrue(snapshot.hasMore)
    }

    private fun legacyEvent(
        id: Long,
        content: String,
        receiveDateMs: Long,
        packageName: String = "com.example.app",
    ) = EventInfoForDisplay(
        id = id,
        packageName = packageName,
        configOptions = emptySet(),
        channel = "default",
        receiveDate = Date(receiveDateMs),
        title = "title-$id",
        content = content,
        event = ManagerEvent(
            id = id,
            packageName = packageName,
            configOptions = emptySet(),
            channel = "default",
            receiveDateMs = receiveDateMs,
            title = "title-$id",
            content = content,
        ),
    )

    private fun displayEvent(
        id: Long,
        type: Int,
        result: Int,
        receiveDateMs: Long = id,
        packageName: String = "com.example.app",
        configOptions: Set<String> = emptySet(),
    ) = EventInfoForDisplay(
        id = id,
        packageName = packageName,
        configOptions = configOptions,
        channel = "default",
        receiveDate = Date(receiveDateMs),
        title = "title-$id",
        content = "content-$id",
        event = ManagerEvent(
            id = id,
            packageName = packageName,
            configOptions = configOptions,
            channel = "default",
            receiveDateMs = receiveDateMs,
            title = "title-$id",
            content = "content-$id",
            type = type,
            result = result,
        ),
    )

    private fun event(id: Long) = EventInfoForDisplay(
        id = id,
        packageName = "com.example.app",
        configOptions = emptySet(),
        channel = "default",
        receiveDate = Date(id),
        title = "title-$id",
        content = "content-$id",
    )
}
