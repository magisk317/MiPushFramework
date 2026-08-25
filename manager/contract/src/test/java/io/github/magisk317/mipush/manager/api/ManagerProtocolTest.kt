package io.github.magisk317.mipush.manager.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerProtocolTest {
    @Test
    fun `matching major negotiates the lower minor`() {
        val compatibility = ManagerProtocol.evaluateCompatibility(
            clientMajor = 1,
            clientMinor = 4,
            runtimeMajor = 1,
            runtimeMinor = 2,
        )

        assertTrue(compatibility.isCompatible)
        assertEquals(2, compatibility.negotiatedMinor)
        assertNull(compatibility.reason)
    }

    @Test
    fun `major mismatch blocks only protocol compatibility`() {
        val compatibility = ManagerProtocol.evaluateCompatibility(
            clientMajor = 2,
            clientMinor = 0,
            runtimeMajor = 1,
            runtimeMinor = 8,
        )

        assertFalse(compatibility.isCompatible)
        assertNull(compatibility.negotiatedMinor)
        assertEquals("protocol_major_mismatch", compatibility.reason)
    }

    @Test
    fun `invalid versions return a typed incompatibility`() {
        val compatibility = ManagerProtocol.evaluateCompatibility(
            clientMajor = ManagerProtocol.MAJOR,
            clientMinor = -1,
        )

        assertFalse(compatibility.isCompatible)
        assertEquals(ManagerProtocol.CompatibilityStatus.INVALID_VERSION, compatibility.status)
        assertEquals("invalid_protocol_version", compatibility.reason)
    }

    @Test
    fun `unknown runtime capabilities are ignored`() {
        val capabilities = ManagerProtocol.recognizedCapabilities(
            listOf(
                "future_capability",
                ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT,
                ManagerProtocol.CAPABILITY_APPLICATION_LIST,
                ManagerProtocol.CAPABILITY_APPLICATION_DETAIL,
                ManagerProtocol.CAPABILITY_APPLICATION_DIAGNOSTICS,
                ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT,
            ),
        )

        assertEquals(
            setOf(
                ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT,
                ManagerProtocol.CAPABILITY_APPLICATION_LIST,
                ManagerProtocol.CAPABILITY_APPLICATION_DETAIL,
                ManagerProtocol.CAPABILITY_APPLICATION_DIAGNOSTICS,
            ),
            capabilities,
        )
    }

    @Test
    fun `application query page size is bounded by negotiated maximum`() {
        val query = ManagerApplicationQueryDto(pageSize = 51)

        assertEquals(
            "invalid_application_page_size",
            ManagerProtocol.validateApplicationQuery(query, negotiatedMaxPageSize = 50),
        )
    }

    @Test
    fun `event query requires an explicit nonnegative user`() {
        assertEquals(
            "invalid_event_user_id",
            ManagerProtocol.validateEventQuery(
                ManagerEventQueryDto(userId = -1),
                negotiatedMaxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
            ),
        )
    }

    @Test
    fun `application query rejects unknown filter modes`() {
        assertEquals(
            "invalid_application_filter_mode",
            ManagerProtocol.validateApplicationQuery(
                ManagerApplicationQueryDto(filterMode = 99),
                negotiatedMaxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
            ),
        )
    }

    @Test
    fun `application page rejects oversized collections and tokens`() {
        val oversizedPage = ManagerApplicationPageDto(
            items = List(3) { applicationSummary(it.toLong()) },
            stats = ManagerApplicationStatsDto(total = 3, usingMiPush = 3, registered = 3),
            nextPageToken = "token",
        )

        assertEquals(
            "too_many_application_page_items",
            ManagerProtocol.validateApplicationPage(oversizedPage, negotiatedMaxPageSize = 2),
        )
        assertEquals(
            "application_next_page_token_too_long",
            ManagerProtocol.validateApplicationPage(
                oversizedPage.copy(
                    items = emptyList(),
                    stats = ManagerApplicationStatsDto(),
                    nextPageToken = "x".repeat(ManagerProtocol.MAX_PAGE_TOKEN_LENGTH + 1),
                ),
                negotiatedMaxPageSize = 2,
            ),
        )
    }

    @Test
    fun `application page rejects items from another user`() {
        val page = ManagerApplicationPageDto(
            userId = 0,
            items = listOf(applicationSummary(1L).copy(userId = 999)),
            stats = ManagerApplicationStatsDto(total = 1, usingMiPush = 1),
        )

        assertEquals(
            "invalid_application_user_id",
            ManagerProtocol.validateApplicationPage(page, negotiatedMaxPageSize = 1),
        )
    }

    @Test
    fun `application page is bounded by the negotiated payload size`() {
        val page = ManagerApplicationPageDto(
            items = listOf(applicationSummary(1L)),
            stats = ManagerApplicationStatsDto(total = 1, usingMiPush = 1, registered = 1),
        )
        val payloadBytes = ManagerProtocol.estimateApplicationPageWireBytes(page).toInt()

        assertEquals(
            "application_page_payload_too_large",
            ManagerProtocol.validateApplicationPage(
                page = page,
                negotiatedMaxPageSize = 1,
                negotiatedMaxPayloadBytes = payloadBytes - 1,
            ),
        )
        assertNull(
            ManagerProtocol.validateApplicationPage(
                page = page,
                negotiatedMaxPageSize = 1,
                negotiatedMaxPayloadBytes = payloadBytes,
            ),
        )
    }

    @Test
    fun `application diagnostics rejects negative counts`() {
        assertEquals(
            "invalid_application_diagnostics_reg_sec_count",
            ManagerProtocol.validateApplicationDiagnostics(
                ManagerApplicationDiagnosticsDto(regSecCount = -1),
            ),
        )
    }

    @Test
    fun `notification channel protocol rejects empty groups but accepts ungrouped channels`() {
        assertEquals(
            "invalid_notification_channel_group_id",
            ManagerProtocol.validateNotificationChannelGroupSummary(
                ManagerNotificationChannelGroupSummaryDto(id = "", name = "Synthetic"),
            ),
        )
        assertNull(
            ManagerProtocol.validateNotificationChannelSummary(
                ManagerNotificationChannelSummaryDto(
                    id = "ungrouped",
                    name = "Ungrouped",
                    importance = 3,
                    groupId = null,
                ),
            ),
        )
    }

    @Test
    fun `application request package names and registration types are bounded`() {
        assertEquals(
            "invalid_application_package_name",
            ManagerProtocol.validateApplicationPackageName("x".repeat(ManagerProtocol.MAX_PACKAGE_NAME_LENGTH + 1)),
        )
        assertEquals(
            "invalid_application_package_name",
            ManagerProtocol.validateApplicationPackageName("com.example/bad"),
        )
        assertEquals(
            "invalid_application_registered_type",
            ManagerProtocol.validateApplicationDiagnosticsRequest("com.example.app", registeredType = 99),
        )
    }

    @Test
    fun `handshake compatibility reason is a bounded machine code`() {
        val handshake = ManagerHandshake(
            protocolMajor = ManagerProtocol.MAJOR,
            protocolMinor = ManagerProtocol.MINOR,
            runtimeVersionName = "test",
            runtimeVersionCode = 1L,
            supportedCapabilities = emptyList(),
            maxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
            maxPayloadBytes = ManagerProtocol.DEFAULT_MAX_PAYLOAD_BYTES,
            compatibilityReason = "private path /data/user",
        )

        assertEquals("invalid_compatibility_reason", ManagerProtocol.validateHandshake(handshake))
    }

    private fun applicationSummary(id: Long) = ManagerApplicationSummaryDto(
        id = id,
        packageName = "com.example.$id",
        appName = "Example $id",
    )
}
