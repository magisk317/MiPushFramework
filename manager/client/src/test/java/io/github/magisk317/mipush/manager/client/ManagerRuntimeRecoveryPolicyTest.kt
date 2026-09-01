package io.github.magisk317.mipush.manager.client

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ManagerRuntimeRecoveryPolicyTest {
    @Test
    fun `reconnect remains scheduled before the configured attempt limit`() {
        assertEquals(
            ManagerRuntimeReconnectAction.ScheduleReconnect,
            ManagerRuntimeRecoveryPolicy.reconnectAction(
                reconnectAttempt = 2,
                maxReconnectAttempts = 3,
                recoveryIssued = false,
                recoveryAvailable = true,
            ),
        )
    }

    @Test
    fun `first exhausted cycle invokes recovery only when recovery is available`() {
        assertEquals(
            ManagerRuntimeReconnectAction.RecoverRuntime,
            ManagerRuntimeRecoveryPolicy.reconnectAction(
                reconnectAttempt = 3,
                maxReconnectAttempts = 3,
                recoveryIssued = false,
                recoveryAvailable = true,
            ),
        )
        assertEquals(
            ManagerRuntimeReconnectAction.FailExhausted,
            ManagerRuntimeRecoveryPolicy.reconnectAction(
                reconnectAttempt = 3,
                maxReconnectAttempts = 3,
                recoveryIssued = false,
                recoveryAvailable = false,
            ),
        )
    }

    @Test
    fun `recovery is issued at most once per reconnect cycle`() {
        assertEquals(
            ManagerRuntimeReconnectAction.FailExhausted,
            ManagerRuntimeRecoveryPolicy.reconnectAction(
                reconnectAttempt = 3,
                maxReconnectAttempts = 3,
                recoveryIssued = true,
                recoveryAvailable = true,
            ),
        )
    }

    @Test
    fun `recovery target rejects an invalid UID without falling back to primary user`() {
        assertEquals(-1, ManagerRuntimeRecoveryPolicy.androidUserId(-1))
        assertEquals(0, ManagerRuntimeRecoveryPolicy.androidUserId(99_999))
        assertEquals(1, ManagerRuntimeRecoveryPolicy.androidUserId(100_000))
        assertEquals(999, ManagerRuntimeRecoveryPolicy.androidUserId(99_900_001))
    }
}
