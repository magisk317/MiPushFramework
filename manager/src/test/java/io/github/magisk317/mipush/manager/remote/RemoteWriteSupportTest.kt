package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import java.util.UUID
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RemoteWriteSupportTest {
    @Test
    fun `success and duplicate statuses are treated as successful writes`() {
        assertTrue(
            RemoteWriteSupport.isSuccess(
                ManagerWriteResultDto(
                    requestId = "1",
                    status = ManagerProtocol.WRITE_STATUS_SUCCESS,
                    details = "ok",
                ),
            ),
        )
        assertTrue(
            RemoteWriteSupport.isSuccess(
                ManagerWriteResultDto(
                    requestId = "1",
                    status = ManagerProtocol.WRITE_STATUS_DUPLICATE,
                    details = "dup",
                ),
            ),
        )
        assertFalse(
            RemoteWriteSupport.isSuccess(
                ManagerWriteResultDto(
                    requestId = "1",
                    status = ManagerProtocol.WRITE_STATUS_UNSUPPORTED,
                    details = "no",
                ),
            ),
        )
        assertFalse(RemoteWriteSupport.isSuccess(null))
    }

    @Test
    fun `default request IDs are unique UUIDs`() {
        val first = RemoteWriteSupport.resolveRequestId()
        val second = RemoteWriteSupport.resolveRequestId()

        UUID.fromString(first)
        UUID.fromString(second)
        assertNotEquals(first, second)
        assertTrue(first.isNotBlank())
        assertTrue(first.length <= ManagerProtocol.MAX_WRITE_REQUEST_ID_LENGTH)
    }

    @Test
    fun `explicit request ID is reused for transport retry`() {
        val requestId = "same-transfer-retry"

        assertEquals(requestId, RemoteWriteSupport.resolveRequestId(requestId))
    }
}
