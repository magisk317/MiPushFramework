package com.xiaomi.push.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushConnectionStatusRuntimeTest {

    @Test
    fun `connecting from disconnected dispatches connection started`() {
        val plan = PushConnectionStatusRuntime.planStatusChange(
            currentStatus = 2,
            newStatus = 0
        )

        assertFalse(plan.shouldRemoveConnectingTimeout)
        assertEquals(PushConnectionListenerEvent.ConnectionStarted, plan.listenerEvent)
        assertNull(plan.warningMessage)
    }

    @Test
    fun `connected from connecting dispatches reconnection success`() {
        val plan = PushConnectionStatusRuntime.planStatusChange(
            currentStatus = 0,
            newStatus = 1
        )

        assertTrue(plan.shouldRemoveConnectingTimeout)
        assertEquals(PushConnectionListenerEvent.ReconnectionSuccessful, plan.listenerEvent)
        assertNull(plan.warningMessage)
    }

    @Test
    fun `disconnected from connecting dispatches reconnection failed`() {
        val plan = PushConnectionStatusRuntime.planStatusChange(
            currentStatus = 0,
            newStatus = 2
        )

        assertTrue(plan.shouldRemoveConnectingTimeout)
        assertEquals(PushConnectionListenerEvent.ReconnectionFailed, plan.listenerEvent)
    }

    @Test
    fun `disconnected from connected dispatches connection closed`() {
        val plan = PushConnectionStatusRuntime.planStatusChange(
            currentStatus = 1,
            newStatus = 2
        )

        assertTrue(plan.shouldRemoveConnectingTimeout)
        assertEquals(PushConnectionListenerEvent.ConnectionClosed, plan.listenerEvent)
    }

    @Test
    fun `connected without connecting first keeps warning`() {
        val plan = PushConnectionStatusRuntime.planStatusChange(
            currentStatus = 2,
            newStatus = 1
        )

        assertEquals("try set connected while not connecting.", plan.warningMessage)
    }

    @Test
    fun `connecting from non disconnected state keeps warning`() {
        val plan = PushConnectionStatusRuntime.planStatusChange(
            currentStatus = 1,
            newStatus = 0
        )

        assertEquals("try set connecting while not disconnected.", plan.warningMessage)
    }
}
