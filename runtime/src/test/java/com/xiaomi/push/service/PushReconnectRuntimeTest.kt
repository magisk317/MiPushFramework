package com.xiaomi.push.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushReconnectRuntimeTest {

    @Test
    fun `initial reconnect has zero delay without prior connect`() {
        val plan = PushReconnectRuntime.computeDelayedReconnect(
            state = PushReconnectRuntime.initialState().copy(attempts = 1),
            nowMs = 1000L
        )

        assertEquals(0, plan.delayMs)
    }

    @Test
    fun `stale prior connect resets short live counters`() {
        val plan = PushReconnectRuntime.computeDelayedReconnect(
            state = PushReconnectState(
                attempts = 1,
                shortLiveConnCount = 3,
                curDelay = 2000,
                lastConnectTime = 0L
            ).copy(lastConnectTime = 1000L),
            nowMs = 1000L + 310000L
        )

        assertEquals(0, plan.delayMs)
        assertEquals(1000, plan.nextState.curDelay)
        assertEquals(0, plan.nextState.shortLiveConnCount)
    }

    @Test
    fun `short live reconnect escalates delay`() {
        val plan = PushReconnectRuntime.computeDelayedReconnect(
            state = PushReconnectState(
                attempts = 1,
                shortLiveConnCount = 1,
                curDelay = 1000,
                lastConnectTime = 5000L
            ),
            nowMs = 6000L
        )

        assertEquals(1000, plan.delayMs)
        assertEquals(1500, plan.nextState.curDelay)
        assertEquals(2, plan.nextState.shortLiveConnCount)
    }

    @Test
    fun `successful connect resets reconnect state`() {
        val state = PushReconnectRuntime.onConnectSucceeded(999L)

        assertEquals(0, state.attempts)
        assertEquals(0, state.shortLiveConnCount)
        assertEquals(500, state.curDelay)
        assertEquals(999L, state.lastConnectTime)
    }

    @Test
    fun `immediate reconnect increments attempts only when no pending job`() {
        val state = PushReconnectRuntime.initialState()

        val immediate = PushReconnectRuntime.planReconnect(
            state = state,
            forceImmediate = true,
            shouldReconnect = true,
            hasPendingConnectJob = false,
            nowMs = 1000L
        )
        val replaceExisting = PushReconnectRuntime.planReconnect(
            state = state,
            forceImmediate = true,
            shouldReconnect = true,
            hasPendingConnectJob = true,
            nowMs = 1000L
        )

        assertEquals(PushReconnectAction.Immediate, immediate.action)
        assertEquals(1, immediate.nextState.attempts)
        assertEquals(PushReconnectAction.Immediate, replaceExisting.action)
        assertEquals(0, replaceExisting.nextState.attempts)
    }

    @Test
    fun `delayed reconnect emits diagnostics on later attempts`() {
        val secondAttempt = PushReconnectRuntime.planReconnect(
            state = PushReconnectRuntime.initialState().copy(attempts = 1, lastConnectTime = 1000L),
            forceImmediate = false,
            shouldReconnect = true,
            hasPendingConnectJob = false,
            nowMs = 2000L
        )
        val thirdAttempt = PushReconnectRuntime.planReconnect(
            state = PushReconnectRuntime.initialState().copy(attempts = 2, lastConnectTime = 1000L),
            forceImmediate = false,
            shouldReconnect = true,
            hasPendingConnectJob = false,
            nowMs = 2000L
        )

        assertTrue(secondAttempt.shouldDumpNativeNetInfo)
        assertFalse(secondAttempt.shouldRunConnectivityTest)
        assertFalse(thirdAttempt.shouldDumpNativeNetInfo)
        assertTrue(thirdAttempt.shouldRunConnectivityTest)
    }
}
