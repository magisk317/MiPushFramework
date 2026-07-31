package io.github.magisk317.mipush.manager.connection

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConnectionReconnectRequesterTest {
    @Test
    fun `request result follows the remote write status`() = runBlocking {
        val accepted = RemoteConnectionReconnectRequester {
            ManagerWriteResultDto(
                requestId = "accepted",
                status = ManagerProtocol.WRITE_STATUS_SUCCESS,
                details = "ok",
            )
        }
        val rejected = RemoteConnectionReconnectRequester {
            ManagerWriteResultDto(
                requestId = "rejected",
                status = ManagerProtocol.WRITE_STATUS_FAILED,
                details = "failed",
            )
        }

        assertTrue(accepted.requestReconnect())
        assertFalse(rejected.requestReconnect())
    }
}
