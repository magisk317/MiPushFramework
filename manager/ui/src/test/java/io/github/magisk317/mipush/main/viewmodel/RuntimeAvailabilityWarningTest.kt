package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeAvailabilityWarningTest {
    @Test
    fun `manager only runtime missing requires blocking warning`() {
        assertTrue(ManagerRuntimeAvailability.RuntimeMissing.requiresRuntimeWarning())
    }

    @Test
    fun `stock or unsupported runtime with successful handshake but missing capabilities warns`() {
        val availability = ManagerRuntimeAvailability.Available(
            handshake = handshake(supportedCapabilities = emptyList()),
        )

        assertTrue(availability.requiresRuntimeWarning())
    }

    @Test
    fun `compatible runtime with required capabilities does not warn`() {
        val availability = ManagerRuntimeAvailability.Available(
            handshake = handshake(
                supportedCapabilities = listOf(
                    ManagerProtocol.CAPABILITY_EVENT_LIST,
                    ManagerProtocol.CAPABILITY_RUNTIME_PREFERENCES,
                    ManagerProtocol.CAPABILITY_RUNTIME_ENVIRONMENT,
                ),
            ),
        )

        assertFalse(availability.requiresRuntimeWarning())
    }

    @Test
    fun `binding states do not flash a warning`() {
        assertFalse(ManagerRuntimeAvailability.Binding.requiresRuntimeWarning())
        assertFalse(ManagerRuntimeAvailability.Disconnected.requiresRuntimeWarning())
    }


    @Test
    fun `connection and protocol failures do not trigger the missing runtime warning`() {
        val handshake = handshake(supportedCapabilities = emptyList())

        assertFalse(ManagerRuntimeAvailability.PermissionDenied.requiresRuntimeWarning())
        assertFalse(ManagerRuntimeAvailability.TimedOut.requiresRuntimeWarning())
        assertFalse(ManagerRuntimeAvailability.Failed("bind_failed").requiresRuntimeWarning())
        assertFalse(
            ManagerRuntimeAvailability.TemporarilyDisconnected(
                reason = io.github.magisk317.mipush.manager.client.DisconnectReason.BINDER_DIED,
            ).requiresRuntimeWarning(),
        )
        assertFalse(
            ManagerRuntimeAvailability.Incompatible(
                handshake = handshake,
                reason = "protocol_incompatible",
            ).requiresRuntimeWarning(),
        )
    }
    private fun handshake(supportedCapabilities: List<String>) = ManagerHandshake(
        protocolMajor = ManagerProtocol.MAJOR,
        protocolMinor = ManagerProtocol.MINOR,
        runtimeVersionName = "test",
        runtimeVersionCode = 1,
        supportedCapabilities = supportedCapabilities,
        maxPageSize = ManagerProtocol.DEFAULT_MAX_PAGE_SIZE,
        maxPayloadBytes = ManagerProtocol.DEFAULT_MAX_PAYLOAD_BYTES,
    )
}
