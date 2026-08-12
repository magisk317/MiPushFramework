package com.xiaomi.push.service

import android.app.Application
import com.xiaomi.push.service.timers.Alarm
import com.xiaomi.smack.Connection
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

// Keep Robolectric: this test relies on Android framework implementations indirectly;
// android.jar unit-test stubs throw "Method ... not mocked" without the extension.
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class XMPushServiceConnectionLifecycleTest {
    @AfterEach
    fun tearDown() {
        Alarm.setIAlarm(null)
    }

    @Test
    fun `challenge success runs stock service actions`() {
        val service = mockk<XMPushServiceCore>(relaxed = true)
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val reconnectManager = mockk<ReconnectionManager>(relaxed = true)
        val alarm = RecordingAlarm()
        every { service.runtimeObserver } returns observer
        every { service.reconnectionManager } returns reconnectManager
        every { service.shouldFalldown() } returns false
        Alarm.setIAlarm(alarm)
        val connection = mockk<Connection>(relaxed = true)

        XMPushServiceLifecycleDelegate(service).reconnectionSuccessful(connection)

        verify(exactly = 1) { observer.reconnectionSuccessful(connection) }
        verify(exactly = 1) { service.broadcastNetworkAvailable(true) }
        verify(exactly = 1) { reconnectManager.onConnectSucceeded() }
        assertEquals(listOf(true), alarm.registrations)
    }

    @Test
    fun `connection failure publishes unavailable then reconnects outside fall down`() {
        val service = mockk<XMPushServiceCore>(relaxed = true)
        every { service.runtimeObserver } returns mockk(relaxed = true)
        every { service.shouldFalldown() } returns false
        val connection = mockk<Connection>(relaxed = true)
        val error = IllegalStateException("handshake failed")

        XMPushServiceLifecycleDelegate(service).reconnectionFailed(connection, error)

        verify(exactly = 1) { service.broadcastNetworkAvailable(false) }
        verify(exactly = 1) { service.scheduleConnect(false) }
    }

    @Test
    fun `closed connection respects stock fall down reconnect suppression`() {
        val service = mockk<XMPushServiceCore>(relaxed = true)
        every { service.runtimeObserver } returns mockk(relaxed = true)
        every { service.shouldFalldown() } returns true
        val connection = mockk<Connection>(relaxed = true)

        XMPushServiceLifecycleDelegate(service).connectionClosed(connection, 22, null)

        verify(exactly = 0) { service.scheduleConnect(any()) }
    }

    private class RecordingAlarm : Alarm.IAlarm {
        val registrations = mutableListOf<Boolean>()

        override fun isAlive(): Boolean = false

        override fun registerPing(force: Boolean) {
            registrations += force
        }

        override fun stop() = Unit
    }
}
