package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
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
