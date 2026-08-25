package com.xiaomi.push.service

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class ReconnectionManagerTest {
    @Test
    fun `immediate reconnect atomically replaces the connect job`() {
        val action = mockk<IPushServiceAction>(relaxed = true)
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        every { action.runtimeObserver } returns observer
        every { action.isConnected } returns false
        every { action.hasJob(XMPushServiceJob.TYPE_CONNECT) } returns false
        every {
            observer.resolveReconnectAttemptPlan(
                any(),
                true,
                false,
                false,
            )
        } returns PushReconnectAttemptPlan(
            action = PushReconnectAction.Immediate,
            eventAction = "reconnect_immediate",
        )

        ReconnectionManager(action).tryReconnect(forceReconnect = true)

        verify(exactly = 1) {
            action.replaceJobs(
                XMPushServiceJob.TYPE_CONNECT,
                match { it is ConnectJob },
            )
        }
        verify(exactly = 0) { action.removeJobs(XMPushServiceJob.TYPE_CONNECT) }
        verify(exactly = 0) { action.executeJob(any()) }
    }
}
