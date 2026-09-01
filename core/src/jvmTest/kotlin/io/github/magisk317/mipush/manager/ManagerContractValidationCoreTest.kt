package io.github.magisk317.mipush.manager

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ManagerContractValidationCoreTest {
    @Test
    fun `application page validation preserves ordered failures and payload bounds`() {
        val input = ApplicationPageValidationInput(
            schemaVersion = 1,
            userId = 0,
            itemUserIds = listOf(0),
            summaries = listOf(
                ApplicationSummaryValidationInput(1, "com.example.app", "Example", "Example", 0),
            ),
            stats = ApplicationStatsValidationInput(1, 1, 1, 0, 1, 0),
            nextPageToken = null,
            negotiatedMaxPageSize = 1,
            negotiatedMaxPayloadBytes = 512,
            wireSize = ApplicationPageWireSizeInput(
                items = listOf(ApplicationSummaryWireSizeInput(false, "com.example.app", "Example", "Example")),
                nextPageToken = null,
            ),
        )

        assertNull(ManagerContractValidationCore.validateApplicationPage(input))
        assertEquals(
            "application_page_payload_too_large",
            ManagerContractValidationCore.validateApplicationPage(input.copy(negotiatedMaxPayloadBytes = 1)),
        )
    }

    @Test
    fun `event and channel queries reject invalid negotiated bounds`() {
        assertEquals(
            "invalid_negotiated_page_size",
            ManagerContractValidationCore.validateEventQuery(
                EventQueryValidationInput(1, null, 1, "", "", 0, 0),
            ),
        )
        assertEquals(
            "invalid_negotiated_page_size",
            ManagerContractValidationCore.validateNotificationChannelQuery(
                NotificationChannelQueryValidationInput(1, "com.example.app", 1, null, 0, 0),
            ),
        )
    }

    @Test
    fun `write request validates argument length in the core`() {
        assertEquals(
            "write_argument_too_long",
            ManagerContractValidationCore.validateWriteRequest(
                WriteRequestValidationInput(1, "request", "operation", "", 0, null, 0, 4_097),
            ),
        )
    }

    @Test
    fun `application identity validation preserves user-first failure ordering`() {
        assertEquals(
            "invalid_application_user_id",
            ManagerContractValidationCore.validateApplicationSummary(
                ApplicationSummaryValidationInput(1, "bad/package", "", "", -1),
            ),
        )
        assertEquals(
            "invalid_application_user_id",
            ManagerContractValidationCore.validateApplicationDetail(
                ApplicationDetailValidationInput(1, "bad/package", "", "", -1),
            ),
        )
    }

    @Test
    fun `compatibility core negotiates the lower minor and filters capabilities`() {
        assertEquals(
            ManagerNegotiationResult(ManagerNegotiationStatus.COMPATIBLE, 2),
            ManagerContractCore.evaluateCompatibility(1, 3, 1, 2),
        )
        assertEquals(
            linkedSetOf("known"),
            ManagerContractCore.recognizedCapabilities(listOf("known", "unknown", "known"), setOf("known")),
        )
    }

    @Test
    fun `client policy keeps incompatible peers unavailable and bounds reconnect delay`() {
        val unavailable = ManagerClientPolicyCore.classifyHandshakeSignals(
            compatible = false,
            compatibilityReason = "protocol_major_mismatch",
            validationReason = null,
            handshakeWarning = "ignored",
        )
        assertEquals(false, unavailable.available)
        assertEquals("protocol_major_mismatch", unavailable.reason)
        assertEquals(null, unavailable.warning)
        assertEquals(500L, ManagerClientPolicyCore.reconnectDelayMillis(0))
        assertEquals(10_000L, ManagerClientPolicyCore.reconnectDelayMillis(99))
    }
}
