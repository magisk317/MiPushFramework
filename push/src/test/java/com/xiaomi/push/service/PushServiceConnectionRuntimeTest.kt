package com.xiaomi.push.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushServiceConnectionRuntimeTest {

    @Test
    fun `connect plan skips existing connecting session`() {
        val plan = PushServiceConnectionRuntime.planConnect(
            isConnecting = true,
            isConnected = false
        )

        assertEquals(PushConnectionAttemptAction.SkipConnecting, plan.action)
        assertEquals("connect_skip_connecting", plan.eventAction)
    }

    @Test
    fun `check alive disconnects and reconnects when network is gone`() {
        val plan = PushServiceConnectionRuntime.planCheckAlive(
            isConnected = true,
            hasNetwork = false
        )

        assertEquals(PushCheckAliveAction.DisconnectAndReconnect, plan.action)
        assertEquals("checkalive_disconnect_reconnect", plan.eventAction)
    }

    @Test
    fun `reconnection failure only schedules retry when not falling down`() {
        val plan = PushServiceConnectionRuntime.planReconnectionFailure(shouldFalldown = false)

        assertTrue(plan.shouldBroadcastUnavailable)
        assertTrue(plan.shouldScheduleReconnect)
        assertEquals("reconnect_failed_schedule", plan.eventAction)
    }

    @Test
    fun `reconnection failure stops scheduling retry during falldown`() {
        val plan = PushServiceConnectionRuntime.planReconnectionFailure(shouldFalldown = true)

        assertTrue(plan.shouldBroadcastUnavailable)
        assertFalse(plan.shouldScheduleReconnect)
        assertEquals("reconnect_failed_falldown", plan.eventAction)
    }

    @Test
    fun `reconnection success reactivates alarm only when needed`() {
        val plan = PushServiceConnectionRuntime.planReconnectionSuccess(
            alarmAlive = false,
            shouldFalldown = false
        )

        assertTrue(plan.shouldBroadcastAvailable)
        assertTrue(plan.shouldResetReconnectState)
        assertTrue(plan.shouldRegisterAlarm)
        assertTrue(plan.shouldBindAllClients)
        assertEquals("reconnect_success_alarm_reactivated", plan.eventAction)
    }

    @Test
    fun `reconnection success keeps alarm unchanged during falldown`() {
        val plan = PushServiceConnectionRuntime.planReconnectionSuccess(
            alarmAlive = false,
            shouldFalldown = true
        )

        assertFalse(plan.shouldRegisterAlarm)
        assertEquals("reconnect_success", plan.eventAction)
    }

    @Test
    fun `connection closed schedules reconnect when service can recover`() {
        val plan = PushServiceConnectionRuntime.planConnectionClosed(shouldFalldown = false)

        assertTrue(plan.shouldScheduleReconnect)
        assertEquals("connection_closed_schedule_reconnect", plan.eventAction)
    }

    @Test
    fun `connection closed skips reconnect during falldown`() {
        val plan = PushServiceConnectionRuntime.planConnectionClosed(shouldFalldown = true)

        assertFalse(plan.shouldScheduleReconnect)
        assertEquals("connection_closed_falldown", plan.eventAction)
    }
}
