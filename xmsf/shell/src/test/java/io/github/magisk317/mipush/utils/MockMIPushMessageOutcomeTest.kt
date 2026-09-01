package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MockMIPushMessageOutcomeTest {
    @Test
    fun `typed outcomes have distinct runtime observation actions`() = with(MockMIPushMessage) {
        assertEquals(
            "mock_replay_blocked_by_permission",
            MockReplayOutcome.BlockedByPermission.observationAction(),
        )
        assertEquals("mock_replay_dispatched", MockReplayOutcome.Dispatched.observationAction())
        assertEquals("mock_replay_posted", MockReplayOutcome.Posted.observationAction())
        assertEquals(
            "mock_replay_failed_channel_disabled",
            MockReplayOutcome.FailedChannelDisabled.observationAction(),
        )
        assertEquals("mock_replay_failed", MockReplayOutcome.Failed.observationAction())
    }
}
