package com.xiaomi.push.service

import com.xiaomi.xmsf.runtime.PushChannelState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PushPacketSyncRuntimeTest {

    @Test
    fun `kick wait resolves to rebind plan`() {
        val plan = PushPacketSyncRuntime.resolveKick(
            kickType = "wait",
            kickReason = "server_busy"
        )

        assertEquals(PushKickAction.Rebind, plan.action)
        assertEquals(PushChannelState.Unbound, plan.runtimeState)
        assertTrue(plan.shouldScheduleRebind)
        assertFalse(plan.shouldCloseChannel)
        assertFalse(plan.shouldDeactivateClient)
        assertEquals("server_busy", plan.statusReasonMessage)
    }

    @Test
    fun `kick close resolves to kicked plan`() {
        val plan = PushPacketSyncRuntime.resolveKick(
            kickType = "cancel",
            kickReason = "revoked"
        )

        assertEquals(PushKickAction.Close, plan.action)
        assertEquals(PushChannelState.Kicked, plan.runtimeState)
        assertTrue(plan.shouldCloseChannel)
        assertTrue(plan.shouldDeactivateClient)
        assertFalse(plan.shouldScheduleRebind)
    }

    @Test
    fun `bind success resolves to bound state`() {
        val plan = PushPacketSyncRuntime.resolveBindResult(
            success = true,
            errorType = null,
            errorReason = null
        )

        assertEquals(PushBindAction.Bound, plan.action)
        assertEquals(PushChannelState.Bound, plan.runtimeState)
        assertEquals(PushClientsManager.ClientStatus.binded, plan.clientStatus)
        assertEquals(PushClientsManager.ClientLoginInfo.TYPE_CHANNEL_OPEN_RESULT, plan.notifyType)
        assertFalse(plan.shouldDeactivateClient)
        assertFalse(plan.shouldScheduleRebind)
    }

    @Test
    fun `bind auth invalid sig resolves to deactivate plan`() {
        val plan = PushPacketSyncRuntime.resolveBindResult(
            success = false,
            errorType = "auth",
            errorReason = "invalid-sig"
        )

        assertEquals(PushBindAction.Deactivate, plan.action)
        assertEquals(PushChannelState.OpenFailed, plan.runtimeState)
        assertEquals(PushClientsManager.ClientStatus.unbind, plan.clientStatus)
        assertEquals(5, plan.statusReasonCode)
        assertTrue(plan.shouldDeactivateClient)
        assertTrue(plan.shouldReportInvalidSig)
        assertFalse(plan.shouldScheduleRebind)
    }

    @Test
    fun `bind wait resolves to rebind plan`() {
        val plan = PushPacketSyncRuntime.resolveBindResult(
            success = false,
            errorType = "wait",
            errorReason = "retry_later"
        )

        assertEquals(PushBindAction.Rebind, plan.action)
        assertEquals(PushChannelState.Unbound, plan.runtimeState)
        assertEquals(PushClientsManager.ClientStatus.unbind, plan.clientStatus)
        assertEquals(7, plan.statusReasonCode)
        assertTrue(plan.shouldScheduleRebind)
        assertFalse(plan.shouldDeactivateClient)
    }

    @Test
    fun `bind unknown failure leaves status untouched`() {
        val plan = PushPacketSyncRuntime.resolveBindResult(
            success = false,
            errorType = "other",
            errorReason = "unknown"
        )

        assertEquals(PushBindAction.Ignore, plan.action)
        assertNull(plan.runtimeState)
        assertNull(plan.clientStatus)
        assertNull(plan.notifyType)
        assertFalse(plan.shouldScheduleRebind)
        assertFalse(plan.shouldDeactivateClient)
    }

    @Test
    fun `redirect hosts plan trims and filters blanks`() {
        val plan = PushPacketSyncRuntime.resolveRedirect(" host1 ; ;host2 ")

        assertTrue(plan.shouldReconnect)
        assertEquals(listOf("host1", "host2"), plan.preferredHosts)
    }

    @Test
    fun `redirect empty plan does nothing`() {
        val plan = PushPacketSyncRuntime.resolveRedirect(" ; ")

        assertFalse(plan.shouldReconnect)
        assertTrue(plan.preferredHosts.isEmpty())
    }
}
