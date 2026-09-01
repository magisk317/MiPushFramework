package io.github.magisk317.mipush.manager.api

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ManagerProtocolNegotiationSupportTest {
    @Test
    fun `compatibility decision is neutral and negotiates the lower minor`() {
        val result = ManagerProtocolNegotiationSupport.evaluateCompatibility(
            clientMajor = 1,
            clientMinor = 4,
            runtimeMajor = 1,
            runtimeMinor = 2,
        )

        assertEquals(NegotiationStatus.COMPATIBLE, result.status)
        assertEquals(2, result.negotiatedMinor)
    }

    @Test
    fun `recognized capabilities preserve first-seen order and remove unknowns`() {
        val capabilities = ManagerProtocolNegotiationSupport.recognizedCapabilities(
            runtimeCapabilities = listOf("unknown", "known-b", "known-a", "known-b"),
            knownCapabilities = setOf("known-a", "known-b"),
        )

        assertEquals(linkedSetOf("known-b", "known-a"), capabilities)
    }

    @Test
    fun `validation accepts primitive handshake fields without a parcelable`() {
        val limits = HandshakeValidationLimits(
            maxRuntimeVersionNameLength = 128,
            maxCompatibilityReasonLength = 128,
            maxCapabilityCount = 64,
            maxCapabilityLength = 128,
            maxNegotiatedPageSize = 1_000,
            maxNegotiatedPayloadBytes = 512 * 1024,
        )

        assertNull(
            ManagerProtocolNegotiationSupport.validateHandshake(
                input = HandshakeValidationInput(
                    protocolMajor = 1,
                    protocolMinor = 7,
                    runtimeVersionName = "test",
                    runtimeVersionCode = 1L,
                    supportedCapabilities = listOf("known"),
                    maxPageSize = 50,
                    maxPayloadBytes = 512 * 1024,
                    compatibilityReason = "runtime_degraded",
                ),
                limits = limits,
            ),
        )
    }

    @Test
    fun `validation retains the original ordered reason checks`() {
        val input = HandshakeValidationInput(
            protocolMajor = -1,
            protocolMinor = 7,
            runtimeVersionName = "test",
            runtimeVersionCode = -1L,
            supportedCapabilities = emptyList(),
            maxPageSize = 50,
            maxPayloadBytes = 512 * 1024,
            compatibilityReason = null,
        )

        assertEquals(
            "invalid_protocol_version",
            ManagerProtocolNegotiationSupport.validateHandshake(
                input = input,
                limits = HandshakeValidationLimits(
                    maxRuntimeVersionNameLength = 128,
                    maxCompatibilityReasonLength = 128,
                    maxCapabilityCount = 64,
                    maxCapabilityLength = 128,
                    maxNegotiatedPageSize = 1_000,
                    maxNegotiatedPayloadBytes = 512 * 1024,
                ),
            ),
        )
    }
}
