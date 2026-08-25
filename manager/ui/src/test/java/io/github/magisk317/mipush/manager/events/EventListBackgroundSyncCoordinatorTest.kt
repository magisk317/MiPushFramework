package io.github.magisk317.mipush.manager.events

import io.github.magisk317.mipush.feature.main.subpage.EventInfoForDisplay
import java.util.Date
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class EventListBackgroundSyncCoordinatorTest {
    @Test
    fun `incoming events replace same ids and result is newest first`() {
        val existing = listOf(event(id = 1L, time = 100L), event(id = 2L, time = 200L))
        val incoming = listOf(event(id = 1L, time = 300L), event(id = 3L, time = 150L))

        val merged = mergeEventSnapshots(existing, incoming)

        assertEquals(listOf(1L, 2L, 3L), merged.map { it.id })
        assertEquals(300L, merged.first().receiveDate.time)
    }

    @Test
    fun `legacy events deduplicate by stable display key`() {
        val existing = listOf(event(id = 0L, time = 100L, title = "same"))
        val incoming = listOf(event(id = 0L, time = 100L, title = "same"))

        assertEquals(1, mergeEventSnapshots(existing, incoming).size)
    }

    private fun event(id: Long, time: Long, title: String = "event-$id") = EventInfoForDisplay(
        id = id,
        packageName = "com.example",
        configOptions = emptySet(),
        channel = "default",
        receiveDate = Date(time),
        title = title,
        content = "content",
    )
}
