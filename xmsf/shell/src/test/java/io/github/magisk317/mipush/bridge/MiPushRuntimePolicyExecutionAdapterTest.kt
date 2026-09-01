package io.github.magisk317.mipush.bridge

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushRuntimePolicyExecutionAdapterTest {
    @Test
    fun `empty active clients use stock disconnect policy`() {
        val plan = MiPushRuntimePolicyExecutionAdapter.resolveClientChangePlan(
            activeClientCount = 0,
            shouldUpdateAlarm = true,
        )

        assertTrue(plan.shouldDisconnect)
        assertTrue(plan.shouldUpdateAlarm)
        assertEquals("client_change_disconnect", plan.eventAction)
    }
}
