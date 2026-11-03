package io.github.magisk317.mipush.manager.application

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MockReplayOutcomeTest {
    @Test
    fun `replay outcomes remain distinct terminal values`() {
        val outcomes = listOf(
            MockReplayOutcome.BlockedByPermission,
            MockReplayOutcome.Dispatched,
            MockReplayOutcome.PostedTarget,
            MockReplayOutcome.PostedLocalFallback,
            MockReplayOutcome.FailedChannelDisabled,
            MockReplayOutcome.Failed,
        )

        assertEquals(6, outcomes.toSet().size)
    }
}
