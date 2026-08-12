package io.github.magisk317.mipush.manager.runtime.read

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import android.app.NotificationManager
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

// Keep Robolectric: this test relies on Android framework implementations indirectly;
// android.jar unit-test stubs throw "Method ... not mocked" without the extension.
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class ManagerNotificationChannelRuntimeReaderTest {
    @Test
    fun `synthetic empty group is dropped while ungrouped channel stays ungrouped`() {
        val ungrouped = NotificationChannel(
            "ungrouped",
            "Ungrouped",
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        val grouped = NotificationChannel(
            "grouped",
            "Grouped",
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            group = "real"
        }
        val reader = ManagerNotificationChannelRuntimeReader(
            isHookedProvider = { true },
            channelProvider = { listOf(ungrouped, grouped) },
            groupProvider = {
                listOf(
                    NotificationChannelGroup(null, null),
                    NotificationChannelGroup("", "Blank"),
                    NotificationChannelGroup("real", "Real"),
                )
            },
            channelEnricher = { _, channels -> channels },
        )

        val page = reader.readPage(
            ManagerNotificationChannelReadQuery(
                packageName = "com.example.app",
                pageSize = 20,
            ),
        )
        val channelsById = page.items.associateBy(ManagerNotificationChannelReadSummary::id)

        assertEquals(listOf("real"), page.groups.map(ManagerNotificationChannelGroupReadSummary::id))
        assertFalse(page.groups.any { it.id.isBlank() })
        assertNull(channelsById.getValue("ungrouped").groupId)
        assertEquals("real", channelsById.getValue("grouped").groupId)
    }

    @Test
    fun `rejects a query for another runtime user`() {
        val reader = ManagerNotificationChannelRuntimeReader(
            userIdProvider = { 0 },
        )

        assertThrows(IllegalArgumentException::class.java) {
            reader.readPage(
                ManagerNotificationChannelReadQuery(
                    packageName = "com.example",
                    userId = 999,
                ),
            )
        }
    }
}
