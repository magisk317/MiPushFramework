package com.xiaomi.slim

import android.content.ContextWrapper
import com.xiaomi.push.service.IPushServiceAction
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.smack.Connection
import com.xiaomi.smack.ConnectionConfiguration
import com.xiaomi.smack.packet.Packet
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.lang.reflect.Proxy

class BinderTest {
    @Test
    fun `bind generates non-empty packetID for signature calculation`() {
        var sentBlob: Blob? = null

        val actionProxy = Proxy.newProxyInstance(
            IPushServiceAction::class.java.classLoader,
            arrayOf(IPushServiceAction::class.java),
        ) { _, _, _ -> null } as IPushServiceAction

        val fakeContext = ContextWrapper(null)
        val config = ConnectionConfiguration(emptyMap(), 5222, null)

        val connection = object : Connection(actionProxy, fakeContext, config) {
            override val host: String = "test.mipush"
            override val isBinaryConnection: Boolean = true
            override fun connect() = Unit
            override fun disconnect(reason: Int, error: Exception?) = Unit
            override fun initConnection() = Unit
            override fun send(blob: Blob) {
                sentBlob = blob
            }
            override fun batchSend(blobArray: Array<Blob>) = Unit
            override fun sendPingInternal(isServerPing: Boolean) = Unit
            override fun sendPacket(packet: Packet) = Unit
            override fun batchSendPacket(packetArr: Array<Packet>) = Unit
            override fun bind(clientLoginInfo: PushClientsManager.ClientLoginInfo) = Unit
            override fun unbind(chid: String, userId: String) = Unit
        }

        val client = PushClientsManager.ClientLoginInfo().apply {
            chid = "5"
            userId = "123456@xiaomi.com/test"
            token = "dummy_token"
            security = "dummy_sec"
            authMethod = "XMPUSH-PASS"
            pkgName = "com.xiaomi.xmsf"
        }

        Binder.bind(client, "test-challenge", connection)

        assertNotNull(sentBlob)
        val packetId = sentBlob?.packetID
        assertNotNull(packetId)
        assertFalse(packetId.isNullOrEmpty())
        assertTrue(packetId!!.isNotEmpty())
    }
}
