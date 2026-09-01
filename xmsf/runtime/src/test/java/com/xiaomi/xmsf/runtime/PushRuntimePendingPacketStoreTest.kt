package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PushRuntimePendingPacketStoreTest {

    @Test
    fun `cached registration errors update runtime state and invoke notifier`() {
        AndroidPushRuntime.clearStateForTests()
        PushRuntimePendingPacketStore.clearForTests()
        val notifications = mutableListOf<String>()

        PushRuntimePendingPacketStore.cacheRegistrationRequest("com.example.one", byteArrayOf(1, 2, 3), androidUserId = 0)
        PushRuntimePendingPacketStore.cacheRegistrationRequest("com.example.two", byteArrayOf(4, 5, 6), androidUserId = 0)

        val notified = PushRuntimePendingPacketStore.notifyRegisterError(
            errorCode = 70000002,
            errorMessage = "no account",
            notifier = { packageName, payload, errorCode, errorMessage ->
                notifications += "$packageName:$errorCode:$errorMessage:${payload.size}"
            },
            androidUserId = 0,
        )

        assertEquals(2, notified)
        assertEquals(2, notifications.size)
        assertEquals(PushRegistrationState.Failed, AndroidPushRuntime.getRegistrationRecord("com.example.one", androidUserId = 0)?.state)
        assertEquals(PushRegistrationState.Failed, AndroidPushRuntime.getRegistrationRecord("com.example.two", androidUserId = 0)?.state)
        assertEquals(0, PushRuntimePendingPacketStore.pendingRegistrationCount(androidUserId = 0))
    }

    @Test
    fun `pending messages preserve order and requeue unsent tail on failure`() {
        AndroidPushRuntime.clearStateForTests()
        PushRuntimePendingPacketStore.clearForTests()
        val first = byteArrayOf(1)
        val second = byteArrayOf(2)
        val delivered = mutableListOf<Pair<String, ByteArray>>()

        PushRuntimePendingPacketStore.addPendingMessage("com.example.one", first, androidUserId = 0)
        PushRuntimePendingPacketStore.addPendingMessage("com.example.two", second, androidUserId = 0)

        runCatching {
            PushRuntimePendingPacketStore.processPendingMessages("test", androidUserId = 0, sender = { packageName, payload ->
                delivered += packageName to payload
                if (packageName == "com.example.two") {
                    throw IllegalStateException("boom")
                }
            })
        }

        assertEquals(2, delivered.size)
        assertEquals(1, PushRuntimePendingPacketStore.pendingMessageCount(androidUserId = 0))

        val flushed = mutableListOf<Pair<String, ByteArray>>()
        val count = PushRuntimePendingPacketStore.processPendingMessages("retry", androidUserId = 0, sender = { packageName, payload ->
            flushed += packageName to payload
        })

        assertEquals(1, count)
        assertEquals("com.example.two", flushed.single().first)
        assertArrayEquals(second, flushed.single().second)
    }

    @Test
    fun `failed registration flush preserves newer same-package request`() {
        PushRuntimePendingPacketStore.clearForTests()
        val packageName = "com.example.target"
        val oldPayload = byteArrayOf(1)
        val newPayload = byteArrayOf(2)
        PushRuntimePendingPacketStore.cacheRegistrationRequest(packageName, oldPayload, androidUserId = 0)

        runCatching {
            PushRuntimePendingPacketStore.processPendingRegistrationRequests("test", androidUserId = 0, sender = { _, _ ->
                PushRuntimePendingPacketStore.cacheRegistrationRequest(packageName, newPayload, androidUserId = 0)
                throw IllegalStateException("boom")
            })
        }

        val flushed = mutableListOf<ByteArray>()
        val count = PushRuntimePendingPacketStore.processPendingRegistrationRequests("retry", androidUserId = 0, sender = { _, payload ->
            flushed += payload
        })

        assertEquals(1, count)
        assertArrayEquals(newPayload, flushed.single())
    }

    @Test
    fun `discard rejects invalid user ids instead of falling back to primary`() {
        PushRuntimePendingPacketStore.clearForTests()

        assertThrows<IllegalArgumentException> {
            PushRuntimePendingPacketStore.discardPackage("com.example.target", -1)
        }
    }
}
