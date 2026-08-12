package io.github.magisk317.mipush.manager.api

import android.app.Application
import android.os.Parcel
import android.os.Parcelable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ManagerPhase2ParcelableTest {
    @Test
    fun `event protocol parcelables round trip`() {
        val summary = ManagerEventSummaryDto(
            userId = 999,
            id = 42L,
            packageName = "com.example.app",
            configOptions = listOf("disable", "sound"),
            channel = "default",
            receiveDateMs = 1_700_000_000_000L,
            title = "Hello",
            content = "World",
            appName = "Example",
            type = 0,
            result = 0,
            info = "meta",
            payload = byteArrayOf(1, 2, 3),
            regSec = "secret",
        )
        assertEquals(summary, roundTrip(summary, ManagerEventSummaryDto.CREATOR))
        assertEquals(
            ManagerEventQueryDto(
                lastId = 41L,
                pageSize = 20,
                packageName = "com.example.app",
                query = "hello",
                userId = 999,
            ),
            roundTrip(
                ManagerEventQueryDto(
                    lastId = 41L,
                    pageSize = 20,
                    packageName = "com.example.app",
                    query = "hello",
                    userId = 999,
                ),
                ManagerEventQueryDto.CREATOR,
            ),
        )
        assertEquals(
            ManagerEventPageDto(items = listOf(summary)),
            roundTrip(ManagerEventPageDto(items = listOf(summary)), ManagerEventPageDto.CREATOR),
        )
    }

    @Test
    fun `notification and configuration parcelables round trip`() {
        val channel = ManagerNotificationChannelSummaryDto(
            id = "ch_com.example",
            name = "Default",
            importance = 3,
            groupId = "grp",
            description = "desc",
            enabled = true,
            managedByMiPush = true,
        )
        val group = ManagerNotificationChannelGroupSummaryDto(
            id = "grp",
            name = "Group",
            managedByMiPush = true,
        )
        val page = ManagerNotificationChannelPageDto(
            packageName = "com.example.app",
            isHooked = true,
            items = listOf(channel),
            groups = listOf(group),
            nextPageToken = "token",
        )
        assertEquals(page, roundTrip(page, ManagerNotificationChannelPageDto.CREATOR))

        val catalog = ManagerConfigurationCatalogDto(
            sourceRepo = "owner/repo",
            branch = "main",
            generatedAt = "2026-07-21T00:00:00Z",
            files = listOf(
                ManagerConfigurationCatalogEntryDto(
                    path = "APP/example.json",
                    name = "example.json",
                    sha = "abc",
                    size = 12,
                    updatedAt = "2026-07-21T00:00:00Z",
                ),
            ),
        )
        assertEquals(catalog, roundTrip(catalog, ManagerConfigurationCatalogDto.CREATOR))
    }

    @Test
    fun `log export result without descriptor round trips`() {
        val result = ManagerLogExportResultDto(
            success = false,
            details = "missing",
            parcelFileDescriptor = null,
        )
        val restored = roundTrip(result, ManagerLogExportResultDto.CREATOR)
        assertEquals(result.success, restored.success)
        assertEquals(result.details, restored.details)
        assertEquals(null, restored.parcelFileDescriptor)
    }

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

    private fun <T : Parcelable> roundTrip(value: T, creator: Parcelable.Creator<T>): T {
        val parcel = Parcel.obtain()
        try {
            value.writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            return creator.createFromParcel(parcel)
        } finally {
            parcel.recycle()
        }
    }
}
