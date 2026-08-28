package io.github.magisk317.mipush.runtime.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushConnectionPlanFactoryTest {
    @Test
    fun `connect and check alive decisions retain runtime contract`() {
        assertEquals(
            PushConnectionAttemptAction.SkipConnecting,
            PushConnectionPlanFactory.planConnect(isConnecting = true, isConnected = false).action,
        )
        assertEquals(
            "connect_start",
            PushConnectionPlanFactory.planConnect(isConnecting = false, isConnected = false).eventAction,
        )
        assertEquals(
            PushCheckAliveAction.DisconnectAndReconnect,
            PushConnectionPlanFactory.planCheckAlive(isConnected = true, hasNetwork = false).action,
        )
    }

    @Test
    fun `network decisions preserve deferred reset check and connect precedence`() {
        val deferred = PushConnectionPlanFactory.planNetworkChanged(
            hasNetwork = true,
            isNetworkDeferred = true,
            isConnected = false,
            isConnecting = false,
            shouldResetOnWifi = true,
            shouldCheckAlive = true,
        )
        val reset = PushConnectionPlanFactory.planNetworkChanged(
            hasNetwork = true,
            isNetworkDeferred = false,
            isConnected = true,
            isConnecting = false,
            shouldResetOnWifi = true,
            shouldCheckAlive = true,
        )
        val disconnected = PushConnectionPlanFactory.planNetworkChanged(
            hasNetwork = false,
            isNetworkDeferred = false,
            isConnected = true,
            isConnecting = false,
            shouldResetOnWifi = false,
            shouldCheckAlive = true,
        )

        assertEquals("network_changed_deferred", deferred.eventAction)
        assertFalse(deferred.shouldUpdateAlarm)
        assertEquals("network_changed_wifi_reset", reset.eventAction)
        assertTrue(reset.shouldResetConnection)
        assertFalse(reset.shouldCheckAlive)
        assertEquals("network_changed_disconnect", disconnected.eventAction)
        assertTrue(disconnected.shouldDisconnect)
    }

    @Test
    fun `screen and timer plans stop alarm during falldown`() {
        val screen = PushConnectionPlanFactory.planScreenState(
            isScreenOn = false,
            shouldFalldown = true,
            alarmAlive = true,
            isConnected = true,
            isConnecting = false,
        )
        val timer = PushConnectionPlanFactory.planTimer(
            shouldFalldown = true,
            alarmAlive = true,
            isConnected = true,
            isConnecting = false,
            shouldCheckAlive = true,
        )

        assertTrue(screen.shouldStopAlarm)
        assertEquals("screen_off_falldown_stop", screen.eventAction)
        assertTrue(timer.shouldStopAlarm)
        assertFalse(timer.shouldRegisterPing)
        assertEquals("timer_falldown_stop", timer.eventAction)
    }

    @Test
    fun `client and reconnect policies retain boundary behavior`() {
        val noClients = PushConnectionPlanFactory.planClientChange(
            activeClientCount = 0,
            shouldUpdateAlarm = true,
        )
        val reconnect = PushConnectionPlanFactory.planShouldReconnect(
            hasNetwork = true,
            activeClientCount = 1,
            pushDisabled = false,
            pushEnabled = true,
            superPowerMode = false,
            extremePowerMode = false,
        )
        val noReconnect = PushConnectionPlanFactory.planShouldReconnect(
            hasNetwork = true,
            activeClientCount = 0,
            pushDisabled = false,
            pushEnabled = true,
            superPowerMode = false,
            extremePowerMode = false,
        )

        assertTrue(noClients.shouldDisconnect)
        assertEquals("client_change_disconnect", noClients.eventAction)
        assertTrue(reconnect.shouldReconnect)
        assertEquals("should_reconnect", reconnect.eventAction)
        assertFalse(noReconnect.shouldReconnect)
        assertEquals("should_not_reconnect", noReconnect.eventAction)
    }

    @Test
    fun `power and connection close plans preserve reason contracts`() {
        val extreme = PushConnectionPlanFactory.planPowerModeChanged(
            isExtremePowerMode = true,
            isSuperPowerMode = false,
            isConnected = true,
        )
        val normalClose = PushConnectionPlanFactory.planConnectionClosed(
            shouldFalldown = true,
            failedConnection = false,
        )
        val failedClose = PushConnectionPlanFactory.planConnectionClosed(
            shouldFalldown = true,
            failedConnection = true,
        )

        assertEquals(23, extreme.disconnectReason)
        assertEquals("power_mode_extreme_disconnect", extreme.eventAction)
        assertFalse(normalClose.shouldScheduleReconnect)
        assertEquals("connection_closed_falldown", normalClose.eventAction)
        assertTrue(failedClose.shouldScheduleReconnect)
        assertEquals("connection_closed_schedule_reconnect", failedClose.eventAction)
    }

    @Test
    fun `reconnection success only reactivates alarm outside falldown`() {
        val success = PushConnectionPlanFactory.planReconnectionSuccess(
            alarmAlive = false,
            shouldFalldown = false,
        )
        val falldown = PushConnectionPlanFactory.planReconnectionFailure(shouldFalldown = true)

        assertTrue(success.shouldRegisterAlarm)
        assertEquals("reconnect_success_alarm_reactivated", success.eventAction)
        assertFalse(falldown.shouldScheduleReconnect)
        assertEquals("reconnect_failed_falldown", falldown.eventAction)
    }
}
