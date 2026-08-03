package io.github.magisk317.mipush.manager.notification

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteNotificationChannelCommandTest {
    @Test
    fun `delete sends package and channel id as a domain write command`() = runBlocking {
        var captured: Triple<String, String, String>? = null
        val command = RemoteNotificationChannelCommand { operation, packageName, argument ->
            captured = Triple(operation, packageName, argument)
            ManagerWriteResultDto(
                requestId = "request",
                status = ManagerProtocol.WRITE_STATUS_SUCCESS,
            )
        }

        assertTrue(command.delete(" com.example ", " channel-id "))
        assertEquals(
            Triple(
                ManagerProtocol.WRITE_OP_DELETE_NOTIFICATION_CHANNEL,
                "com.example",
                "channel-id",
            ),
            captured,
        )
    }

    @Test
    fun `delete rejects blank identity without sending a write`() = runBlocking {
        var calls = 0
        val command = RemoteNotificationChannelCommand { _, _, _ ->
            calls += 1
            null
        }

        assertFalse(command.delete("", "channel-id"))
        assertFalse(command.delete("com.example", " "))
        assertEquals(0, calls)
    }
}
