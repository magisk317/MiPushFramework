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
    fun `reconnect delay is bounded`() {
        assertEquals(500L, ManagerRuntimeClientPolicy.reconnectDelayMillis(0))
        assertEquals(1_000L, ManagerRuntimeClientPolicy.reconnectDelayMillis(1))
        assertEquals(8_000L, ManagerRuntimeClientPolicy.reconnectDelayMillis(4))
        assertEquals(10_000L, ManagerRuntimeClientPolicy.reconnectDelayMillis(20))
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
