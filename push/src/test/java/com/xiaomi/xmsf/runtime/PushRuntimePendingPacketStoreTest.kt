package com.xiaomi.xmsf.runtime

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PushRuntimePendingPacketStoreTest {

    @Test
    fun `cached registration errors update runtime state and invoke notifier`() {
        PushRuntime.clearStateForTests()
        PushRuntimePendingPacketStore.clearForTests()
        val notifications = mutableListOf<String>()

        PushRuntimePendingPacketStore.cacheRegistrationRequest("com.example.one", byteArrayOf(1, 2, 3))
        PushRuntimePendingPacketStore.cacheRegistrationRequest("com.example.two", byteArrayOf(4, 5, 6))

        val notified = PushRuntimePendingPacketStore.notifyRegisterError(
            errorCode = 70000002,
            errorMessage = "no account"
        ) { packageName, payload, errorCode, errorMessage ->
            notifications += "$packageName:$errorCode:$errorMessage:${payload.size}"
        }

        assertEquals(2, notified)
        assertEquals(2, notifications.size)
        assertEquals(PushRegistrationState.Failed, PushRuntime.getRegistrationRecord("com.example.one")?.state)
        assertEquals(PushRegistrationState.Failed, PushRuntime.getRegistrationRecord("com.example.two")?.state)
        assertEquals(0, PushRuntimePendingPacketStore.pendingRegistrationCount())
    }

    @Test
    fun `pending messages preserve order and requeue unsent tail on failure`() {
        PushRuntime.clearStateForTests()
        PushRuntimePendingPacketStore.clearForTests()
        val first = byteArrayOf(1)
        val second = byteArrayOf(2)
        val delivered = mutableListOf<Pair<String, ByteArray>>()

        PushRuntimePendingPacketStore.addPendingMessage("com.example.one", first)
        PushRuntimePendingPacketStore.addPendingMessage("com.example.two", second)

        runCatching {
            PushRuntimePendingPacketStore.processPendingMessages("test") { packageName, payload ->
                delivered += packageName to payload
                if (packageName == "com.example.two") {
                    throw IllegalStateException("boom")
                }
            }
        }

        assertEquals(2, delivered.size)
        assertEquals(1, PushRuntimePendingPacketStore.pendingMessageCount())

        val flushed = mutableListOf<Pair<String, ByteArray>>()
        val count = PushRuntimePendingPacketStore.processPendingMessages("retry") { packageName, payload ->
            flushed += packageName to payload
        }

        assertEquals(1, count)
        assertEquals("com.example.two", flushed.single().first)
        assertArrayEquals(second, flushed.single().second)
    }
}
