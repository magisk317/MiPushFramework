package com.xiaomi.push.service

import com.xiaomi.smack.packet.Message
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class PushPacketRuntimeTest {
    @AfterEach
    fun tearDown() {
        PushClientsManager.getInstance().removeActiveClients()
    }

    @Test
    fun `preparePacket drops packet when package has no open channel`() {
        val result = PushPacketRuntime.preparePacket(
            packet = Message().apply { from = "user@example.com/resource" },
            packageName = "com.example.app",
            session = "session-1",
            pushClientsManager = PushClientsManager.getInstance(),
            connected = true,
        )

        assertEquals(PushPacketRouteAction.DropNoChannel, result.action)
        assertEquals("no_channel", result.reason)
    }

    @Test
    fun `preparePacket drops packet when connection is disconnected`() {
        val client = addClient(
            packageName = "com.example.app",
            channelId = "5",
            userId = "user@example.com/resource",
            session = "session-1",
            status = PushClientsManager.ClientStatus.binded,
        )

        val result = PushPacketRuntime.preparePacket(
            packet = Message().apply { from = client.userId },
            packageName = client.pkgName,
            session = client.session,
            pushClientsManager = PushClientsManager.getInstance(),
            connected = false,
        )

        assertEquals(PushPacketRouteAction.DropDisconnected, result.action)
        assertSame(client, result.client)
        assertEquals("channel_not_connected", result.reason)
    }

    @Test
    fun `preparePacket drops packet when client is not bound`() {
        val client = addClient(
            packageName = "com.example.app",
            channelId = "5",
            userId = "user@example.com/resource",
            session = "session-1",
            status = PushClientsManager.ClientStatus.unbind,
        )

        val result = PushPacketRuntime.preparePacket(
            packet = Message().apply {
                from = client.userId
                channelId = client.chid
            },
            packageName = client.pkgName,
            session = client.session,
            pushClientsManager = PushClientsManager.getInstance(),
            connected = true,
        )

        assertEquals(PushPacketRouteAction.DropUnbound, result.action)
        assertSame(client, result.client)
        assertEquals("channel_not_opened", result.reason)
    }

    @Test
    fun `preparePacket fills missing channel and returns ready for bound client`() {
        val client = addClient(
            packageName = "com.example.app",
            channelId = "5",
            userId = "user@example.com/resource",
            session = "session-1",
            status = PushClientsManager.ClientStatus.binded,
        )
        val packet = Message().apply { from = client.userId }

        val result = PushPacketRuntime.preparePacket(
            packet = packet,
            packageName = client.pkgName,
            session = client.session,
            pushClientsManager = PushClientsManager.getInstance(),
            connected = true,
        )

        assertEquals(PushPacketRouteAction.Ready, result.action)
        assertSame(packet, result.packet)
        assertSame(client, result.client)
        assertEquals(client.chid, packet.channelId)
        assertEquals(client.pkgName, packet.packageName)
    }

    @Test
    fun `preparePacket drops packet when session is invalid`() {
        val client = addClient(
            packageName = "com.example.app",
            channelId = "5",
            userId = "user@example.com/resource",
            session = "session-1",
            status = PushClientsManager.ClientStatus.binded,
        )

        val result = PushPacketRuntime.preparePacket(
            packet = Message().apply {
                from = client.userId
                channelId = client.chid
            },
            packageName = client.pkgName,
            session = "other-session",
            pushClientsManager = PushClientsManager.getInstance(),
            connected = true,
        )

        assertEquals(PushPacketRouteAction.DropInvalidSession, result.action)
        assertSame(client, result.client)
        assertEquals("invalid_session", result.reason)
    }

    private fun addClient(
        packageName: String,
        channelId: String,
        userId: String,
        session: String,
        status: PushClientsManager.ClientStatus,
    ): PushClientsManager.ClientLoginInfo {
        return PushClientsManager.ClientLoginInfo().apply {
            pkgName = packageName
            chid = channelId
            this.userId = userId
            this.session = session
            security = "security"
            this.status = status
            PushClientsManager.getInstance().addActiveClient(this)
        }
    }
}
