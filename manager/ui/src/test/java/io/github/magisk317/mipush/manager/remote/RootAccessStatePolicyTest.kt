package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.common.manager.ManagerRootAccessState
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RootAccessStatePolicyTest {
    @Test
    fun `missing runtime response is unavailable rather than denied`() {
        assertEquals(
            ManagerRootAccessState.UNAVAILABLE,
            resolveRuntimeRootAccessState(null),
        )
    }

    @Test
    fun `successful runtime root states remain distinct`() {
        val granted = ManagerWriteResultDto(
            status = ManagerProtocol.WRITE_STATUS_SUCCESS,
            details = ManagerProtocol.WRITE_DETAIL_ROOT_AVAILABLE,
            resultLong = 1L,
        )
        val denied = ManagerWriteResultDto(
            status = ManagerProtocol.WRITE_STATUS_SUCCESS,
            details = ManagerProtocol.WRITE_DETAIL_ROOT_MISSING,
        )

        assertEquals(ManagerRootAccessState.GRANTED, resolveRuntimeRootAccessState(granted))
        assertEquals(ManagerRootAccessState.NOT_GRANTED, resolveRuntimeRootAccessState(denied))
    }

    @Test
    fun `usage stats result requires an explicit successful allow marker`() {
        assertEquals(
            true,
            resolveUsageStatsAllowed(
                ManagerWriteResultDto(
                    status = ManagerProtocol.WRITE_STATUS_SUCCESS,
                    resultLong = 1L,
                ),
            ),
        )
        assertEquals(
            false,
            resolveUsageStatsAllowed(
                ManagerWriteResultDto(
                    status = ManagerProtocol.WRITE_STATUS_SUCCESS,
                    resultLong = 0L,
                ),
            ),
        )
        assertEquals(false, resolveUsageStatsAllowed(null))
    }
}
