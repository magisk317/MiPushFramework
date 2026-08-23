package com.xiaomi.smack

import android.app.Application
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.smack.packet.Packet
import com.xiaomi.slim.Blob
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension
import java.util.concurrent.CancellationException

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class ConnectionStateMachineTest {
    @Test
    fun `valid challenge is the only connecting to connected transition`() {
        val fixture = fixture()

        assertEquals(ConnectionConfiguration.CONNECT_STATUS_DISCONNECT, fixture.connection.connectStatus)
        assertFalse(fixture.connection.isConnected)
        assertFalse(fixture.connection.isConnecting)

        fixture.connection.connect()

        assertTrue(fixture.connection.isConnecting)
        assertEquals(1, fixture.listener.started)
        assertEquals(0, fixture.listener.succeeded)

        fixture.connection.acceptChallenge("server-challenge")

        assertTrue(fixture.connection.isConnected)
        assertFalse(fixture.connection.isConnecting)
        assertEquals("server-challenge", fixture.connection.challenge)
        assertEquals(1, fixture.listener.succeeded)

        fixture.connection.acceptChallenge("duplicate")

        assertEquals("server-challenge", fixture.connection.challenge)
        assertEquals(1, fixture.listener.succeeded)
    }

    @Test
    fun `disconnect while connecting reports stock cancellation failure`() {
        val fixture = fixture()

        fixture.connection.connect()
        fixture.connection.disconnect(18, null)

        assertFalse(fixture.connection.isConnected)
        assertFalse(fixture.connection.isConnecting)
        assertEquals(1, fixture.listener.failed)
        assertInstanceOf(CancellationException::class.java, fixture.listener.failure)
        assertEquals(0, fixture.listener.closed)
    }

    @Test
    fun `disconnect after challenge reports connection closed`() {
        val fixture = fixture()

        fixture.connection.connect()
        fixture.connection.acceptChallenge("server-challenge")
        fixture.connection.disconnect(22, null)

        assertEquals(ConnectionConfiguration.CONNECT_STATUS_DISCONNECT, fixture.connection.connectStatus)
        assertEquals(1, fixture.listener.closed)
        assertEquals(22, fixture.listener.closeReason)
        assertEquals(0, fixture.listener.failed)
    }

    private fun fixture(): Fixture {
        val action = mockk<IPushServiceAction>(relaxed = true)
        val context: Application = RuntimeEnvironment.getApplication()
        val connection = TestConnection(
            action,
            context,
            ConnectionConfiguration(emptyMap(), 5_227, null),
        )
        val listener = RecordingConnectionListener()
        connection.addConnectionListener(listener)
        return Fixture(connection, listener)
    }

    private data class Fixture(
        val connection: TestConnection,
        val listener: RecordingConnectionListener,
    )

    private class TestConnection(
        action: IPushServiceAction,
        context: Application,
        configuration: ConnectionConfiguration,
    ) : Connection(action, context, configuration) {
        override fun connect() {
            setConnectionStatus(ConnectionConfiguration.CONNECT_STATUS_CONNECTING, 0, null)
        }

        override fun disconnect(reason: Int, error: Exception?) {
            shutdown(reason, error)
        }

        fun acceptChallenge(challenge: String) {
            setChallenge(challenge)
        }

        override val host: String = "test.mipush"

        override val isBinaryConnection: Boolean = true

        override fun initConnection() = Unit

        override fun send(blob: Blob) = Unit

        override fun batchSend(blobArray: Array<Blob>) = Unit

        override fun sendPingInternal(isServerPing: Boolean) = Unit

        override fun sendPacket(packet: Packet) = Unit

        override fun batchSendPacket(packetArr: Array<Packet>) = Unit

        override fun bind(clientLoginInfo: PushClientsManager.ClientLoginInfo) = Unit

        override fun unbind(chid: String, userId: String) = Unit
    }

    private class RecordingConnectionListener : ConnectionListener {
        var started = 0
        var succeeded = 0
        var failed = 0
        var closed = 0
        var failure: Exception? = null
        var closeReason: Int? = null

        override fun connectionClosed(connection: Connection, reason: Int, error: Exception?) {
            closed += 1
            closeReason = reason
        }

        override fun connectionStarted(connection: Connection) {
            started += 1
        }

        override fun reconnectionFailed(connection: Connection, error: Exception) {
            failed += 1
            failure = error
        }

        override fun reconnectionSuccessful(connection: Connection) {
            succeeded += 1
        }
    }
}
