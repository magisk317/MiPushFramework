package com.xiaomi.push.service

import com.xiaomi.smack.packet.IQ
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.`when`
import org.mockito.Mockito.mock

class PushPacketAndChannelInfoRuntimeTest {

    @Test
    fun `prepare packet fills missing channel and returns ready client`() {
        val pushClientsManager = mock(PushClientsManager::class.java)
        val client = PushClientsManager.ClientLoginInfo().apply {
            chid = "9"
            userId = "user@xiaomi.com/resource"
            pkgName = "com.example.app"
            security = "sec"
            session = "session"
            status = PushClientsManager.ClientStatus.binded
        }
        val packet = IQ().apply {
            from = "user@xiaomi.com/resource"
        }
        `when`(pushClientsManager.queryChannelIdByPackage("com.example.app")).thenReturn(listOf("9"))
        `when`(pushClientsManager.getClientLoginInfoByChidAndUserId("9", "user@xiaomi.com/resource")).thenReturn(client)

        val result = PushPacketRuntime.preparePacket(
            packet = packet,
            packageName = "com.example.app",
            session = "session",
            pushClientsManager = pushClientsManager,
            connected = true
        )

        assertEquals(PushPacketRouteAction.Ready, result.action)
        assertEquals("9", result.packet?.channelId)
        assertEquals(client, result.client)
    }

    @Test
    fun `prepare packet drops invalid session`() {
        val pushClientsManager = mock(PushClientsManager::class.java)
        val client = PushClientsManager.ClientLoginInfo().apply {
            chid = "9"
            userId = "user@xiaomi.com/resource"
            pkgName = "com.example.app"
            security = "sec"
            session = "session"
            status = PushClientsManager.ClientStatus.binded
        }
        val packet = IQ().apply {
            channelId = "9"
            from = "user@xiaomi.com/resource"
        }
        `when`(pushClientsManager.queryChannelIdByPackage("com.example.app")).thenReturn(listOf("9"))
        `when`(pushClientsManager.getClientLoginInfoByChidAndUserId("9", "user@xiaomi.com/resource")).thenReturn(client)

        val result = PushPacketRuntime.preparePacket(
            packet = packet,
            packageName = "com.example.app",
            session = "other",
            pushClientsManager = pushClientsManager,
            connected = true
        )

        assertEquals(PushPacketRouteAction.DropInvalidSession, result.action)
        assertNull(result.packet)
    }

    @Test
    fun `channel info update resolves first package channel when missing chid`() {
        val pushClientsManager = mock(PushClientsManager::class.java)
        val client = PushClientsManager.ClientLoginInfo().apply {
            chid = "5"
            userId = "user@xiaomi.com/resource"
            pkgName = "com.example.app"
            clientExtra = "old_client"
            cloudExtra = "old_cloud"
        }
        `when`(pushClientsManager.getAllClientLoginInfoByChid("5")).thenReturn(listOf(client))

        val target = PushChannelInfoRuntime.resolveUpdateTarget(
            packageChannelIds = listOf("5"),
            requestedChannelId = null,
            requestedUserId = null,
            pushClientsManager = pushClientsManager
        )
        val result = PushChannelInfoRuntime.applyUpdate(
            target = target,
            hasClientAttr = true,
            clientAttr = "new_client",
            hasCloudAttr = true,
            cloudAttr = "new_cloud"
        )

        assertNotNull(result.target.client)
        assertEquals(true, result.updatedClientExtra)
        assertEquals(true, result.updatedCloudExtra)
        assertEquals("new_client", client.clientExtra)
        assertEquals("new_cloud", client.cloudExtra)
    }
}
