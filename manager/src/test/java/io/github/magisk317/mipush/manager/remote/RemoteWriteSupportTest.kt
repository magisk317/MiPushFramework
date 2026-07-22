package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import org.junit.jupiter.api.Assertions.assertFalse
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
}
