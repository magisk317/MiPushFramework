package io.github.magisk317.mipush.manager.runtime.read

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ManagerNotificationChannelPageTokenTest {
    @Test
    fun `token round trips for matching package`() {
        val token = ManagerNotificationChannelPageToken.encode("com.example", "channel-2", 0)
        assertEquals("channel-2", ManagerNotificationChannelPageToken.decode("com.example", token, 0))
    }

    @Test
    fun `token rejects package mismatch`() {
        val token = ManagerNotificationChannelPageToken.encode("com.example", "channel-2", 0)
        assertThrows(IllegalArgumentException::class.java) {
            ManagerNotificationChannelPageToken.decode("com.other", token, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            ManagerNotificationChannelPageToken.decode("com.example", token, 999)
        }
    }
}
