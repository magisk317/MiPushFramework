package io.github.magisk317.mipush.runtime

import io.github.magisk317.mipush.runtime.android.AndroidPushRuntime
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntimeObservationAdapter
import io.github.magisk317.mipush.runtime.core.PushConnectionState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PushRuntimeObservationAdapterTest {
    @BeforeEach
    fun resetRuntime() {
        AndroidPushRuntime.clearStateForTests()
    }

    @Test
    fun `adapter publishes connection telemetry to the runtime state`() {
        AndroidPushRuntimeObservationAdapter.observePingSent(10L)
        AndroidPushRuntimeObservationAdapter.observeReadAlive(20L)
        AndroidPushRuntimeObservationAdapter.observePingTimeout(30L)
        AndroidPushRuntimeObservationAdapter.observeDisconnectReason(22)
        AndroidPushRuntimeObservationAdapter.observeReconnectStarted(40L)
        AndroidPushRuntimeObservationAdapter.observeConnectionState(
            state = PushConnectionState.Connected,
            source = "adapter-test",
            host = "push.example.test",
            reason = "reconnected",
            nowMs = 50L,
        )
        AndroidPushRuntimeObservationAdapter.observeReconnectConnected(60L)

        val snapshot = AndroidPushRuntime.connectionSnapshot()

        assertEquals("Connected", snapshot.connectionState)
        assertEquals(1L, snapshot.connectionSessionCount)
        assertEquals(50L, snapshot.connectedAtMs)
        assertEquals(10L, snapshot.lastPingSentAtMs)
        assertEquals(20L, snapshot.lastReadAliveAtMs)
        assertEquals(30L, snapshot.lastPingTimeoutAtMs)
        assertEquals(22, snapshot.lastDisconnectReason)
        assertEquals(40L, snapshot.lastReconnectStartedAtMs)
        assertEquals(60L, snapshot.lastReconnectConnectedAtMs)
        assertEquals("push.example.test", snapshot.serverHost)
    }
}
