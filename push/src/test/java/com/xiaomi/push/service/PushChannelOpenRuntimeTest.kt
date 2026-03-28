package com.xiaomi.push.service

import com.xiaomi.xmsf.runtime.PushChannelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushChannelOpenRuntimeTest {

    @Test
    fun `should rebind when session changes`() {
        assertTrue(
            PushChannelOpenRuntime.shouldRebind(
                channelId = "5",
                existingSession = "old-session",
                requestedSession = "new-session",
                existingSecurity = "same-security",
                requestedSecurity = "same-security"
            )
        )
    }

    @Test
    fun `should rebind when security changes`() {
        assertTrue(
            PushChannelOpenRuntime.shouldRebind(
                channelId = "5",
                existingSession = "same-session",
                requestedSession = "same-session",
                existingSecurity = "old-security",
                requestedSecurity = "new-security"
            )
        )
    }

    @Test
    fun `should not rebind when session and security stay the same`() {
        assertFalse(
            PushChannelOpenRuntime.shouldRebind(
                channelId = "5",
                existingSession = "same-session",
                requestedSession = "same-session",
                existingSecurity = "same-security",
                requestedSecurity = "same-security"
            )
        )
    }

    @Test
    fun `open plan fails immediately without network`() {
        val plan = PushChannelOpenRuntime.decideOpenPlan(
            hasNetwork = false,
            isConnected = false,
            clientStatus = PushClientsManager.ClientStatus.unbind,
            shouldRebind = false
        )

        assertEquals(PushChannelOpenAction.OpenFailedNoNetwork, plan.action)
        assertEquals(PushChannelState.OpenFailed, plan.state)
        assertEquals(2, plan.reasonCode)
    }

    @Test
    fun `open plan schedules connect when service is disconnected`() {
        val plan = PushChannelOpenRuntime.decideOpenPlan(
            hasNetwork = true,
            isConnected = false,
            clientStatus = PushClientsManager.ClientStatus.unbind,
            shouldRebind = false
        )

        assertEquals(PushChannelOpenAction.ScheduleConnect, plan.action)
        assertEquals(PushChannelState.Binding, plan.state)
    }

    @Test
    fun `open plan binds new client before considering rebind`() {
        val plan = PushChannelOpenRuntime.decideOpenPlan(
            hasNetwork = true,
            isConnected = true,
            clientStatus = PushClientsManager.ClientStatus.unbind,
            shouldRebind = true
        )

        assertEquals(PushChannelOpenAction.Bind, plan.action)
    }

    @Test
    fun `open plan rebinds bound client when credentials changed`() {
        val plan = PushChannelOpenRuntime.decideOpenPlan(
            hasNetwork = true,
            isConnected = true,
            clientStatus = PushClientsManager.ClientStatus.binded,
            shouldRebind = true
        )

        assertEquals(PushChannelOpenAction.Rebind, plan.action)
        assertEquals(PushChannelState.Binding, plan.state)
    }

    @Test
    fun `open plan returns already bound for stable bound client`() {
        val plan = PushChannelOpenRuntime.decideOpenPlan(
            hasNetwork = true,
            isConnected = true,
            clientStatus = PushClientsManager.ClientStatus.binded,
            shouldRebind = false
        )

        assertEquals(PushChannelOpenAction.AlreadyBound, plan.action)
        assertEquals(PushChannelState.Bound, plan.state)
    }
}
