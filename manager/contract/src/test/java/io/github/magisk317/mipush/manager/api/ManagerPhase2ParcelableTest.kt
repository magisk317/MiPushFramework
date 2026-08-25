package io.github.magisk317.mipush.manager.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerPhase2ParcelableTest {
    @Test
    fun `protocol recognizes phase 2 capabilities and validates event pages`() {
        assertTrue(ManagerProtocol.KNOWN_CAPABILITIES.contains(ManagerProtocol.CAPABILITY_EVENT_LIST))
        assertTrue(ManagerProtocol.KNOWN_CAPABILITIES.contains(ManagerProtocol.CAPABILITY_LOG_EXPORT))
        assertTrue(ManagerProtocol.MINOR >= 2)

        val page = ManagerEventPageDto(
            items = listOf(
                ManagerEventSummaryDto(
                    userId = 999,
                    id = 1L,
                    packageName = "com.example",
                    title = "t",
                    content = "c",
                ),
            ),
        )
        assertEquals(
            null,
            ManagerProtocol.validateEventPage(page, negotiatedMaxPageSize = 100),
        )
        assertEquals(
            "too_many_event_page_items",
            ManagerProtocol.validateEventPage(
                ManagerEventPageDto(
                    items = List(3) {
                        ManagerEventSummaryDto(userId = 999, id = it.toLong(), packageName = "com.example")
                    },
                ),
                negotiatedMaxPageSize = 2,
            ),
        )
    }
}
