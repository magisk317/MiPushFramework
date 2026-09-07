package com.xiaomi.smack

import android.content.Context
import android.os.SystemClock
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.push.service.IPushRuntimeObserver
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.PushShortConnectionPlan
import com.xiaomi.push.service.PushSocketFailurePlan
import com.xiaomi.push.service.PushSocketHostSelectionPlan
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.Socket

class SocketConnectionPolicyWiringTest {
    private lateinit var observer: IPushRuntimeObserver
    private lateinit var action: IPushServiceAction
    private lateinit var context: Context

    @BeforeEach
    fun setUp() {
        mockkStatic(Network::class)
        mockkStatic(SystemClock::class)
        every { SystemClock.elapsedRealtime() } returns 1_000L
        every { Network.hasNetwork(any()) } returns true
        every { Network.getActiveConnPoint(any()) } returns "wifi"
        observer = mockk(relaxed = true)
        action = mockk(relaxed = true)
        context = mockk(relaxed = true)
        every { action.runtimeObserver } returns observer
    }

    @Test
    fun `connect uses observer candidate host plan`() {
        every { observer.resolveCandidateHosts(any(), any()) } returns
            PushSocketHostSelectionPlan(listOf("planned.example"), "socket_connect_fallback_hosts")
        val connection = TestSocketConnection(action, context, socketFails = false)

        connection.connect()

        assertEquals("planned.example", connection.host)
        verify(exactly = 1) { observer.resolveCandidateHosts(any(), any()) }
    }

    @Test
    fun `connect aborts remaining hosts when retry plan detects network change`() {
        every { observer.resolveCandidateHosts(any(), any()) } returns
            PushSocketHostSelectionPlan(listOf("first", "second"), "socket_connect_fallback_hosts")
        every { observer.planFailureRetry(any(), any()) } returns
            PushSocketFailurePlan(false, "socket_connect_abort_network_changed")
        val connection = TestSocketConnection(action, context, socketFails = true)

        assertThrows(XMPPException::class.java) { connection.connect() }

        assertEquals(1, connection.createdSocketCount)
        verify(exactly = 1) { observer.planFailureRetry("wifi", "wifi") }
    }

    @Test
    fun `failed connection resets state so a reused connection can retry`() {
        every { observer.resolveCandidateHosts(any(), any()) } returns
            PushSocketHostSelectionPlan(listOf("planned.example"), "socket_connect_fallback_hosts")
        every { observer.planFailureRetry(any(), any()) } returns
            PushSocketFailurePlan(false, "socket_connect_abort_network_changed")
        val connection = TestSocketConnection(action, context, socketFails = true)

        assertThrows(XMPPException::class.java) { connection.connect() }

        assertEquals(ConnectionConfiguration.CONNECT_STATUS_DISCONNECT, connection.connectStatus)
        assertFalse(connection.isConnecting)

        connection.allowConnections()
        connection.connect()

        assertEquals(2, connection.createdSocketCount)
        assertTrue(connection.isConnecting)
    }

    @Test
    fun `disconnect evaluates short connection through observer`() {
        every { observer.resolveCandidateHosts(any(), any()) } returns
            PushSocketHostSelectionPlan(listOf("planned.example"), "socket_connect_fallback_hosts")
        every {
            observer.evaluateShortConnection(any(), any(), any(), any(), any(), any())
        } returns PushShortConnectionPlan(1, false, "socket_short_conn_retry")
        val connection = TestSocketConnection(action, context, socketFails = false)
        connection.connect()

        connection.disconnect(18, null)

        verify(exactly = 1) {
            observer.evaluateShortConnection(
                nowElapsed = 1_000L,
                lastConnectedTime = 1_000L,
                hasNetwork = true,
                curShortConnCount = 0,
                networkInterval = 300_000L,
                maxShortConnCount = 2,
            )
        }
    }

    private class TestSocketConnection(
        action: IPushServiceAction,
        context: Context,
        private var socketFails: Boolean,
    ) : SocketConnection(
        action,
        context,
        ConnectionConfiguration(emptyMap(), 5_227, null).apply { setHost("requested.example") },
    ) {
        var createdSocketCount: Int = 0

        override fun createSocket(): Socket {
            createdSocketCount += 1
            return mockk(relaxed = true) {
                if (socketFails) {
                    every { connect(any(), any()) } throws java.io.IOException("connect failed")
                }
            }
        }

        fun allowConnections() {
            socketFails = false
        }

        override fun initConnection() = Unit
        override val isBinaryConnection: Boolean = true
        override fun send(blob: Blob) = Unit
        override fun sendPacket(packet: Packet) = Unit
        override fun bind(clientLoginInfo: PushClientsManager.ClientLoginInfo) = Unit
        override fun unbind(chid: String, userId: String) = Unit
        override fun sendPingInternal(isServerPing: Boolean) = Unit
    }
}
