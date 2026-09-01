package io.github.magisk317.mipush.runtime

import io.github.magisk317.mipush.runtime.android.AndroidPushRuntime
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntimeRegistrationChannelObservationAdapter
import io.github.magisk317.mipush.runtime.core.PushChannelState
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class PushRuntimeRegistrationChannelObservationAdapterTest {
    @BeforeEach
    fun resetRuntime() {
        AndroidPushRuntime.clearStateForTests()
    }

    @Test
    fun `adapter publishes registration and channel observations to runtime state`() {
        val adapter = AndroidPushRuntimeRegistrationChannelObservationAdapter

        adapter.observeRegistrationState(
            packageName = "com.example.app",
            state = PushRegistrationState.Registering,
            source = "registration-state",
            reason = "pending",
            nowMs = 10L,
            androidUserId = 0,
        )
        adapter.observeRegistrationResult(
            packageName = "com.example.app",
            success = true,
            source = "registration-result",
            reason = "ok",
            nowMs = 20L,
            androidUserId = 0,
        )
        adapter.observeUnregistration(
            packageName = "com.example.app",
            source = "unregistration",
            reason = "user_request",
            nowMs = 30L,
            androidUserId = 0,
        )
        adapter.observeChannelEvent("com.example.app", "opened", "channel-event")
        adapter.observeChannelState(
            packageName = "com.example.app",
            channelId = "5",
            userId = "user@example.com",
            session = "session-1",
            state = PushChannelState.Bound,
            source = "channel-state",
            reasonCode = 0,
            reasonMessage = "bound",
            nowMs = 40L,
            androidUserId = 0,
        )

        val registration = AndroidPushRuntime.getRegistrationRecord("com.example.app", androidUserId = 0)
        val channel = AndroidPushRuntime.getChannelRecords().single()
        val snapshot = AndroidPushRuntime.snapshot()

        assertNotNull(registration)
        assertEquals(PushRegistrationState.Unregistered, registration?.state)
        assertEquals(30L, registration?.updatedAtMs)
        assertEquals("unregistration", registration?.source)
        assertEquals("user_request", registration?.reason)
        assertEquals("com.example.app", channel.packageName)
        assertEquals("5", channel.channelId)
        assertEquals(PushChannelState.Bound, channel.state)
        assertEquals(40L, channel.updatedAtMs)
        assertEquals("channel-state", channel.source)
        assertEquals(0, channel.reasonCode)
        assertEquals("bound", channel.reasonMessage)
        assertEquals(2L, snapshot.channelEventCount)
        assertEquals("com.example.app", snapshot.lastRegistrationPackage)
        assertEquals(PushRegistrationState.Unregistered, snapshot.lastRegistrationState)
    }
}
