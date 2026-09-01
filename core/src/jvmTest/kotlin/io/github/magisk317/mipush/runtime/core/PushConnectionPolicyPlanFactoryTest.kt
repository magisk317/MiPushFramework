package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushConnectionPolicyPlanFactoryTest {
    @Test
    fun `status transitions preserve listener and timeout semantics`() {
        val connected = PushConnectionStatusPlanFactory.planStatusChange(0, 1)
        val failed = PushConnectionStatusPlanFactory.planStatusChange(0, 2)
        val closed = PushConnectionStatusPlanFactory.planStatusChange(1, 2)

        assertEquals(PushConnectionListenerEvent.ReconnectionSuccessful, connected.listenerEvent)
        assertTrue(connected.shouldRemoveConnectingTimeout)
        assertNull(connected.warningMessage)
        assertEquals(PushConnectionListenerEvent.ReconnectionFailed, failed.listenerEvent)
        assertEquals(PushConnectionListenerEvent.ConnectionClosed, closed.listenerEvent)
    }

    @Test
    fun `invalid status source retains stock warning`() {
        val connected = PushConnectionStatusPlanFactory.planStatusChange(2, 1)
        val connecting = PushConnectionStatusPlanFactory.planStatusChange(1, 0)

        assertEquals("try set connected while not connecting.", connected.warningMessage)
        assertEquals("try set connecting while not disconnected.", connecting.warningMessage)
    }

    @Test
    fun `rebind requires channel and detects session or security changes`() {
        val missingChannel = PushChannelOpenPlanFactory.planRebind(
            channelId = null,
            existingSession = "old",
            requestedSession = "new",
            existingSecurity = "a",
            requestedSecurity = "b",
        )
        val changed = PushChannelOpenPlanFactory.planRebind(
            channelId = "5",
            existingSession = "old",
            requestedSession = "new",
            existingSecurity = "a",
            requestedSecurity = "b",
        )

        assertFalse(missingChannel.shouldRebind)
        assertTrue(changed.shouldRebind)
        assertTrue(changed.sessionChanged)
        assertTrue(changed.securityChanged)
    }

    @Test
    fun `open plan preserves network connection and binding precedence`() {
        assertEquals(
            PushChannelOpenAction.OpenFailedNoNetwork,
            PushChannelOpenPlanFactory.planOpen(false, false, PushBindingState.Unbound, false).action,
        )
        assertEquals(
            PushChannelOpenAction.ScheduleConnect,
            PushChannelOpenPlanFactory.planOpen(true, false, PushBindingState.Bound, true).action,
        )
        assertEquals(
            PushChannelOpenAction.Bind,
            PushChannelOpenPlanFactory.planOpen(true, true, PushBindingState.Unbound, true).action,
        )
        assertEquals(
            PushChannelOpenAction.Rebind,
            PushChannelOpenPlanFactory.planOpen(true, true, PushBindingState.Bound, true).action,
        )
        assertEquals(
            PushChannelOpenAction.AlreadyBound,
            PushChannelOpenPlanFactory.planOpen(true, true, PushBindingState.Bound, false).action,
        )
    }

    @Test
    fun `reset plan checks inputs in stock order`() {
        assertEquals(
            "missing_channel",
            PushServiceResetConnectionPlanFactory.planReset(false, false, false, false, false).reason,
        )
        assertEquals(
            "security_mismatch",
            PushServiceResetConnectionPlanFactory.planReset(true, true, false, true, false).reason,
        )
        assertEquals(
            "connection_alive",
            PushServiceResetConnectionPlanFactory.planReset(true, true, true, true, true).reason,
        )
        assertEquals(
            PushServiceResetConnectionAction.Reset,
            PushServiceResetConnectionPlanFactory.planReset(true, true, true, true, false).action,
        )
    }

    @Test
    fun `redirect parser trims entries and reconnects only with hosts`() {
        val redirect = PushRedirectPlanFactory.resolve(" host1 ; ;host2 ", ";")
        val empty = PushRedirectPlanFactory.resolve(" ; ", ";")

        assertEquals(listOf("host1", "host2"), redirect.preferredHosts)
        assertTrue(redirect.shouldReconnect)
        assertEquals(emptyList<String>(), empty.preferredHosts)
        assertFalse(empty.shouldReconnect)
    }

    @Test
    fun `connectivity gate preserves interval boundary`() {
        assertTrue(
            PushNetworkCheckPlanFactory.shouldRunConnectivityTest(
                activeCount = 1,
                nowMs = 1_800_000L,
                lastCheckTimeMs = 0L,
                allowStats = true,
                testHostsCount = 1,
            ),
        )
        assertFalse(
            PushNetworkCheckPlanFactory.shouldRunConnectivityTest(
                activeCount = 1,
                nowMs = 1_799_999L,
                lastCheckTimeMs = 0L,
                allowStats = true,
                testHostsCount = 1,
            ),
        )
    }

    @Test
    fun `gateway parser accepts only default route ipv4 token`() {
        assertEquals(
            "192.168.1.1",
            PushNetworkCheckPlanFactory.extractGateway(
                "default via 192.168.1.1 dev wlan0 proto dhcp src 192.168.1.8",
            ),
        )
        assertNull(PushNetworkCheckPlanFactory.extractGateway("10.0.0.0/24 dev wlan0 scope link"))
        assertNull(PushNetworkCheckPlanFactory.extractGateway("default via gateway dev wlan0"))
    }
}
