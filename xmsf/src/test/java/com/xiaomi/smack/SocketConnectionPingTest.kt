package com.xiaomi.smack

import android.app.Application
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.XMPushServiceJob
import com.xiaomi.smack.packet.Packet
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class SocketConnectionPingTest {
    @Test
    fun `ordinary ping schedules stock ten second timeout`() {
        val action = mockk<IPushServiceAction>(relaxed = true)
        val scheduledJob = slot<XMPushServiceJob>()
        every { action.executeJobDelayed(capture(scheduledJob), 10_000L) } just Runs
        val connection = connectedConnection(action)

        connection.sendPing(false)

        assertEquals(1, connection.sentPings)
        scheduledJob.captured.process()
        verify(exactly = 1) { action.disconnect(22, null) }
    }

    @Test
    fun `inbound data after ping satisfies timeout check`() {
        val action = mockk<IPushServiceAction>(relaxed = true)
        val scheduledJob = slot<XMPushServiceJob>()
        every { action.executeJobDelayed(capture(scheduledJob), 10_000L) } just Runs
        val connection = connectedConnection(action)

        connection.sendPing(false)
        connection.setReadAlive()
        scheduledJob.captured.process()

        verify(exactly = 0) { action.disconnect(22, null) }
    }

    @Test
    fun `server ping does not schedule timeout`() {
        val action = mockk<IPushServiceAction>(relaxed = true)
        val connection = connectedConnection(action)

        connection.sendPing(true)

        assertEquals(1, connection.sentPings)
        verify(exactly = 0) { action.executeJobDelayed(any(), any()) }
    }

    private fun connectedConnection(action: IPushServiceAction): TestSocketConnection {
        val context: Application = RuntimeEnvironment.getApplication()
        return TestSocketConnection(
            action,
            context,
            ConnectionConfiguration(emptyMap(), 5_227, null),
        ).also { connection ->
            connection.setConnectionStatus(ConnectionConfiguration.CONNECT_STATUS_CONNECTING, 0, null)
            connection.setChallenge("server-challenge")
        }
    }

    private class TestSocketConnection(
        action: IPushServiceAction,
        context: Application,
        configuration: ConnectionConfiguration,
    ) : SocketConnection(action, context, configuration) {
        var sentPings: Int = 0

        override fun initConnection() = Unit

        override fun sendPingInternal(isServerPing: Boolean) {
            sentPings += 1
        }

        override fun isBinaryConnection(): Boolean = true

        override fun sendPacket(packet: Packet) = Unit

        override fun bind(clientLoginInfo: PushClientsManager.ClientLoginInfo) = Unit

        override fun unbind(chid: String, userId: String) = Unit
    }
}
