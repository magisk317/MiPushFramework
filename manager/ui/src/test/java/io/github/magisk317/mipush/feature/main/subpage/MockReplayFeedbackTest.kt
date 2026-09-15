package io.github.magisk317.mipush.feature.main.subpage

import io.github.magisk317.mipush.manager.application.MockReplayOutcome
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
            R.string.mock_notification_failed_channel_disabled,
            MockReplayOutcome.FailedChannelDisabled.feedbackStringRes(),
        )
        assertEquals(
            R.string.mock_notification_failed_event_not_found,
            MockReplayOutcome.FailedEventNotFound.feedbackStringRes(),
        )
        assertEquals(
            R.string.mock_notification_failed_payload_missing,
            MockReplayOutcome.FailedPayloadMissing.feedbackStringRes(),
        )
        assertEquals(
            R.string.mock_notification_failed_service_not_ready,
            MockReplayOutcome.FailedServiceNotReady.feedbackStringRes(),
        )
        assertEquals(
            R.string.mock_notification_failed_app_not_installed,
            MockReplayOutcome.FailedAppNotInstalled.feedbackStringRes(),
        )
        assertEquals(
            R.string.mock_notification_missing_regsec,
            MockReplayOutcome.FailedMissingRegSec.feedbackStringRes(),
        )
        assertEquals(
            R.string.mock_notification_no_receiver,
            MockReplayOutcome.FailedNoReceiver.feedbackStringRes(),
        )
        assertEquals(
            R.string.mock_notification_failed,
            MockReplayOutcome.Failed.feedbackStringRes(),
        )
    }
}
