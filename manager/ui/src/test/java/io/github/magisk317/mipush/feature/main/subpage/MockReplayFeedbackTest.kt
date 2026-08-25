package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.common.notification.MockReplayOutcome
import io.github.magisk317.mipush.manager.R
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MockReplayFeedbackTest {
    @Test
    fun `every replay outcome maps to explicit user feedback`() {
        assertEquals(
            R.string.mock_notification_blocked_by_permission,
            MockReplayOutcome.BlockedByPermission.feedbackStringRes(),
        )
        assertEquals(
            R.string.mock_notification_dispatched,
            MockReplayOutcome.Dispatched.feedbackStringRes(),
        )
        assertEquals(
            R.string.mock_notification_posted,
            MockReplayOutcome.Posted.feedbackStringRes(),
        )
        assertEquals(
            R.string.mock_notification_failed,
            MockReplayOutcome.Failed.feedbackStringRes(),
        )
    }
}
