package io.github.magisk317.mipush.manager.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManagerWireSizeTest {
    @Test
    fun `empty pages have a stable primitive frame size`() {
        assertEquals(
            16L + 28L + 4L,
            ManagerWireSize.estimateApplicationPage(
                ApplicationPageWireSizeInput(items = emptyList(), nextPageToken = null),
            ),
        )
        assertEquals(8L, ManagerWireSize.estimateEventPage(emptyList()))
        assertEquals(
            20L + 4L,
            ManagerWireSize.estimateNotificationChannelPage(
                NotificationChannelPageWireSizeInput(
                    items = emptyList(),
                    groups = emptyList(),
                    nextPageToken = null,
                ),
            ),
        )
    }

    @Test
    fun `nullable strings use the 4-byte null encoding`() {
        assertEquals(4L, ManagerWireSize.stringBytes(null))
        assertEquals(8L, ManagerWireSize.stringBytes(""))
        assertEquals(8L, ManagerWireSize.stringBytes("a"))
        assertEquals(12L, ManagerWireSize.stringBytes("ab"))
    }

    @Test
    fun `application page facade matches the parcelable-free estimator`() {
        val page = ManagerApplicationPageDto(
            items = listOf(
                ManagerApplicationSummaryDto(
                    id = 1L,
                    packageName = "com.example.app",
                    appName = "Example",
                    appNamePinYin = "example",
                ),
            ),
            stats = ManagerApplicationStatsDto(total = 1, usingMiPush = 1, registered = 1),
            nextPageToken = "next",
        )

        assertEquals(
            ManagerProtocol.estimateApplicationPageWireBytes(page),
            ManagerWireSize.estimateApplicationPage(
                ApplicationPageWireSizeInput(
                    items = listOf(
                        ApplicationSummaryWireSizeInput(
                            idPresent = true,
                            packageName = "com.example.app",
                            appName = "Example",
                            appNamePinYin = "example",
                        ),
                    ),
                    nextPageToken = "next",
                ),
            ),
        )
    }

    @Test
    fun `event page facade matches the parcelable-free estimator`() {
        val page = ManagerEventPageDto(
            items = listOf(
                ManagerEventSummaryDto(
                    packageName = "com.example.app",
                    configOptions = listOf("opt"),
                    channel = "ch",
                    title = "title",
                    content = "content",
                    appName = null,
                    info = "info",
                    payload = byteArrayOf(1, 2, 3),
                    regSec = null,
                ),
            ),
        )

        assertEquals(
            ManagerProtocol.estimateEventPageWireBytes(page),
            ManagerWireSize.estimateEventPage(
                listOf(
                    EventSummaryWireSizeInput(
                        packageName = "com.example.app",
                        configOptions = listOf("opt"),
                        channel = "ch",
                        title = "title",
                        content = "content",
                        appName = null,
                        info = "info",
                        payloadBytes = 3,
                        regSec = null,
                    ),
                ),
            ),
        )
    }
}
