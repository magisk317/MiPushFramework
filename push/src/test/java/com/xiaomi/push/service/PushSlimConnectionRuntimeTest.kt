package com.xiaomi.push.service

import com.xiaomi.slim.Blob
import com.xiaomi.xmsf.runtime.PushConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushSlimConnectionRuntimeTest {

    @Test
    fun `ping blob updates last received and records ping event`() {
        val plan = PushSlimConnectionRuntime.planInboundBlob(
            channelId = 0,
            cmd = Blob.CMD_PING
        )

        assertEquals(PushSlimInboundAction.PingReceived, plan.action)
        assertEquals("slim_ping_received", plan.eventAction)
        assertTrue(plan.shouldUpdateLastReceived)
        assertNull(plan.connectionState)
    }

    @Test
    fun `close blob triggers disconnect plan`() {
        val plan = PushSlimConnectionRuntime.planInboundBlob(
            channelId = 0,
            cmd = Blob.CMD_CLOSE
        )

        assertEquals(PushSlimInboundAction.CloseReceived, plan.action)
        assertEquals("slim_close_received", plan.eventAction)
        assertEquals(PushConnectionState.Disconnected, plan.connectionState)
        assertEquals("server_close_blob", plan.connectionReason)
        assertEquals(13, plan.disconnectReasonCode)
    }

    @Test
    fun `non control blob does not trigger special handling`() {
        val plan = PushSlimConnectionRuntime.planInboundBlob(
            channelId = 5,
            cmd = "MSG"
        )

        assertEquals(PushSlimInboundAction.None, plan.action)
        assertNull(plan.eventAction)
        assertFalse(plan.shouldUpdateLastReceived)
        assertNull(plan.disconnectReasonCode)
    }

    @Test
    fun `send ping emits runtime event`() {
        val plan = PushSlimConnectionRuntime.planSendPing()

        assertEquals("slim_ping_sent", plan.eventAction)
    }
}
