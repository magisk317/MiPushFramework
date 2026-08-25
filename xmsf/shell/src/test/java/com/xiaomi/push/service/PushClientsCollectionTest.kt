package com.xiaomi.push.service

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushClientsCollectionTest {
    @Test
    fun `addActiveClient indexes clients by chid and user`() {
        val collection = PushClientsCollection()
        val first = newClient(chid = "5", userId = "alice@example.com")
        val second = newClient(chid = "5", userId = "bob@example.com")

        collection.addActiveClient(first)
        collection.addActiveClient(second)

        assertEquals(1, collection.getActiveClientCount())
        assertEquals(setOf(first, second), collection.getAllClientLoginInfoByChid("5").toSet())
        assertSame(first, collection.getClientLoginInfoByChidAndUserId("5", "alice@example.com"))
        assertSame(second, collection.getClientLoginInfoByChidAndUserId("5", "bob@example.com"))
    }

    @Test
    fun `deactivateClient removes only target and keeps chid until empty`() {
        val collection = PushClientsCollection()
        val first = newClient(chid = "5", userId = "alice@example.com")
        val second = newClient(chid = "5", userId = "bob@example.com")

        collection.addActiveClient(first)
        collection.addActiveClient(second)
        collection.deactivateClient("5", "alice@example.com")

        assertEquals(1, collection.getActiveClientCount())
        assertEquals(listOf(second), collection.getAllClientLoginInfoByChid("5").toList())

        collection.deactivateClient("5", "bob@example.com")

        assertEquals(0, collection.getActiveClientCount())
        assertTrue(collection.getAllClientLoginInfoByChid("5").isEmpty())
    }

    @Test
    fun `listeners are notified on add and deactivate operations`() {
        val collection = PushClientsCollection()
        var changes = 0

        collection.addClientChangeListener { changes++ }
        collection.addActiveClient(newClient(chid = "5", userId = "alice@example.com"))
        collection.deactivateAllClientByChid("5")

        assertEquals(2, changes)
    }

    private fun newClient(chid: String, userId: String): PushClientsManager.ClientLoginInfo {
        return PushClientsManager.ClientLoginInfo().apply {
            this.chid = chid
            this.userId = userId
        }
    }
}
