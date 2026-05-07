package com.xiaomi.push.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushClientStatusSupportTest {
    @Test
    fun `getDesc returns stable labels`() {
        assertEquals("OPEN", PushClientStatusSupport.getDesc(1))
        assertEquals("CLOSE", PushClientStatusSupport.getDesc(2))
        assertEquals("KICK", PushClientStatusSupport.getDesc(3))
        assertEquals("unknown", PushClientStatusSupport.getDesc(99))
    }

    @Test
    fun `computeNotifyDelay depends on peer support state`() {
        val client = PushClientsManager.ClientLoginInfo()

        assertEquals(0, PushClientStatusSupport.computeNotifyDelay(client))

        client.notifiedStatus = PushClientsManager.ClientStatus.unbind
        client.hasPeerSupport = true

        assertEquals(10100, PushClientStatusSupport.computeNotifyDelay(client))
    }

    @Test
    fun `shouldNotifyClient skips recovered status and missing peer`() {
        val client = PushClientsManager.ClientLoginInfo().apply {
            chid = "5"
            status = PushClientsManager.ClientStatus.binded
            notifiedStatus = PushClientsManager.ClientStatus.binded
            hasPeerSupport = true
        }

        assertFalse(PushClientStatusSupport.shouldNotifyClient(client, 1, 0, null))

        client.notifiedStatus = PushClientsManager.ClientStatus.binding
        assertFalse(PushClientStatusSupport.shouldNotifyClient(client, 1, 0, null))

        client.hasPeerSupport = false
        assertTrue(PushClientStatusSupport.shouldNotifyClient(client, 1, 0, null))
    }
}
