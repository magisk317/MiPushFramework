package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
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
    fun `stableRequestId is deterministic for identical write material`() {
        val first = RemoteWriteSupport.stableRequestId(
            operation = ManagerProtocol.WRITE_OP_DELETE_EVENT,
            packageName = "com.example.app",
            eventId = 42L,
        )
        val second = RemoteWriteSupport.stableRequestId(
            operation = ManagerProtocol.WRITE_OP_DELETE_EVENT,
            packageName = "com.example.app",
            eventId = 42L,
        )
        assertEquals(first, second)
        assertTrue(first.isNotBlank())
        assertTrue(first.length <= ManagerProtocol.MAX_WRITE_REQUEST_ID_LENGTH)
    }

    @Test
    fun `stableRequestId changes when logical arguments change`() {
        val base = RemoteWriteSupport.stableRequestId(
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
            longArgument = 100L,
        )
        val differentCutoff = RemoteWriteSupport.stableRequestId(
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
            longArgument = 200L,
        )
        val differentOp = RemoteWriteSupport.stableRequestId(
            operation = ManagerProtocol.WRITE_OP_DELETE_EVENT,
            longArgument = 100L,
        )
        assertNotEquals(base, differentCutoff)
        assertNotEquals(base, differentOp)
    }
}
