package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.mipush.runtime.core.PushChannelRecord
import io.github.magisk317.mipush.runtime.core.PushChannelState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuntimeDeterministicCoordinatorTest {
    @Test
    fun `package scopes normalize explicit user ids`() {
        assertEquals("0:com.example.app", RuntimeDeterministicCoordinator.packageScope("com.example.app", -7))
        assertEquals("12:com.example.app", RuntimeDeterministicCoordinator.packageScope("com.example.app", 12))
    }

    @Test
    fun `message and action scopes use the current user`() {
        val userId = RuntimeDeterministicCoordinator.currentUserId()

        assertEquals(
            "$userId:com.example.app:message-1",
            RuntimeDeterministicCoordinator.messageScope("com.example.app", "message-1")
        )
        assertEquals(
            "$userId:com.example.app:SendMessage",
            RuntimeDeterministicCoordinator.actionScope("com.example.app", "SendMessage")
        )
    }

    @Test
    fun `build reason preserves source when reason is blank`() {
        assertEquals("network", RuntimeDeterministicCoordinator.buildReason("network", null))
        assertEquals("network", RuntimeDeterministicCoordinator.buildReason("network", "  "))
        assertEquals("network:available", RuntimeDeterministicCoordinator.buildReason("network", "available"))
    }

    @Test
    fun `channel identity includes normalized user and channel dimensions`() {
        val record = PushChannelRecord(
            packageName = "com.example.app",
            channelId = "5",
            userId = "user@example.com",
            session = "session-1",
            state = PushChannelState.Bound,
            updatedAtMs = 1L,
            source = "test",
        )

        assertEquals(
            "0:5:com.example.app:user@example.com:session-1",
            RuntimeDeterministicCoordinator.channelIdentity(record, -1)
        )
        assertEquals(
            "7:5:com.example.app:user@example.com:session-1",
            RuntimeDeterministicCoordinator.channelIdentity(record.copy(androidUserId = 7))
        )
    }
}
