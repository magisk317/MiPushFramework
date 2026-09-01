package io.github.magisk317.mipush.manager.client

import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerRuntimeClientPolicyTest {
    @Test
    fun `compatible handshake becomes available`() {
        val handshake = handshake(
            protocolMajor = ManagerProtocol.MAJOR,
            protocolMinor = ManagerProtocol.MINOR + 1,
        )

        val availability = ManagerRuntimeClientPolicy.classifyHandshake(handshake)

        assertEquals(ManagerRuntimeAvailability.Available(handshake), availability)
    }

    @Test
    fun `major mismatch is a typed incompatible state`() {
        val handshake = handshake(
            protocolMajor = ManagerProtocol.MAJOR + 1,
            protocolMinor = 0,
            compatibilityReason = "protocol_major_mismatch",
        )

        val availability = ManagerRuntimeClientPolicy.classifyHandshake(handshake)

        assertTrue(availability is ManagerRuntimeAvailability.Incompatible)
        assertEquals("protocol_major_mismatch", (availability as ManagerRuntimeAvailability.Incompatible).reason)
    }

    @Test
    fun `compatible degraded reason remains available`() {
        val handshake = handshake(
            protocolMajor = ManagerProtocol.MAJOR,
            protocolMinor = ManagerProtocol.MINOR,
            compatibilityReason = "runtime_degraded",
        )

        val availability = ManagerRuntimeClientPolicy.classifyHandshake(handshake)

        assertEquals(
            ManagerRuntimeAvailability.Available(handshake, warning = "runtime_degraded"),
            availability,
        )
    }

    @Test
    fun `malformed limits are a typed incompatibility`() {
        val handshake = handshake(
            protocolMajor = ManagerProtocol.MAJOR,
            protocolMinor = ManagerProtocol.MINOR,
        ).copy(maxPayloadBytes = -1)

        val availability = ManagerRuntimeClientPolicy.classifyHandshake(handshake)

        assertTrue(availability is ManagerRuntimeAvailability.Incompatible)
        assertEquals("invalid_max_payload_bytes", (availability as ManagerRuntimeAvailability.Incompatible).reason)
    }

    @Test
    fun `handshake classification uses primitive signals instead of the parcelable`() {
        val available = ManagerRuntimeClientPolicy.classifyHandshakeSignals(
            compatible = true,
            compatibilityReason = null,
            validationReason = null,
            handshakeWarning = "runtime_degraded",
        )
        assertEquals(true, available.available)
        assertEquals("runtime_degraded", available.warning)

        val incompatible = ManagerRuntimeClientPolicy.classifyHandshakeSignals(
            compatible = false,
            compatibilityReason = "protocol_major_mismatch",
            validationReason = null,
            handshakeWarning = "ignore-me",
        )
        assertEquals(false, incompatible.available)
        assertEquals("protocol_major_mismatch", incompatible.reason)
        assertEquals(null, incompatible.warning)

        val invalid = ManagerRuntimeClientPolicy.classifyHandshakeSignals(
            compatible = true,
            compatibilityReason = null,
            validationReason = "invalid_max_payload_bytes",
            handshakeWarning = "runtime_degraded",
        )
        assertEquals(false, invalid.available)
        assertEquals("invalid_max_payload_bytes", invalid.reason)
    }

    @Test
    fun `reconnect delay is bounded`() {
        assertEquals(500L, ManagerRuntimeClientPolicy.reconnectDelayMillis(0))
        assertEquals(1_000L, ManagerRuntimeClientPolicy.reconnectDelayMillis(1))
        assertEquals(8_000L, ManagerRuntimeClientPolicy.reconnectDelayMillis(4))
        assertEquals(10_000L, ManagerRuntimeClientPolicy.reconnectDelayMillis(20))
    }

    @Test
    fun `reconnect attempts are limited to three before recovery`() {
        assertEquals(3, ManagerRuntimeClientPolicy.DEFAULT_MAX_RECONNECT_ATTEMPTS)
    }

    @Test
    fun `event page timeout is feature local while log export uses the extended session timeout`() {
        val eventPolicy = ManagerRuntimeClientPolicy.eventPageCallPolicy(timeoutMillis = 12_000L)
        val logPolicy = ManagerRuntimeClientPolicy.logExportCallPolicy()

        assertEquals(12_000L, eventPolicy.timeoutMillis)
        assertTrue(!eventPolicy.releaseSessionOnTimeout)
        assertEquals(180_000L, logPolicy.timeoutMillis)
        assertTrue(logPolicy.releaseSessionOnTimeout)
    }

    private fun handshake(
        protocolMajor: Int,
        protocolMinor: Int,
        compatibilityReason: String? = null,
    ) = ManagerHandshake(
        protocolMajor = protocolMajor,
        protocolMinor = protocolMinor,
        runtimeVersionName = "test",
        runtimeVersionCode = 1,
        supportedCapabilities = listOf(ManagerProtocol.CAPABILITY_CONNECTION_SNAPSHOT),
        maxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
        maxPayloadBytes = ManagerProtocol.DEFAULT_MAX_PAYLOAD_BYTES,
        compatibilityReason = compatibilityReason,
    )
}
