package io.github.magisk317.mipush.manager.application

/**
 * Observable result of replaying a stored push notification.
 *
 * [Dispatched] means that the payload entered a legacy/runtime path whose final notification post
 * cannot be observed synchronously. [Posted] is reserved for a confirmed NotificationManager post.
 */
sealed interface MockReplayOutcome {
    data object BlockedByPermission : MockReplayOutcome
    data object Dispatched : MockReplayOutcome
    data object Posted : MockReplayOutcome
    data object FailedChannelDisabled : MockReplayOutcome
    data object FailedEventNotFound : MockReplayOutcome
    data object FailedPayloadMissing : MockReplayOutcome
    data object FailedServiceNotReady : MockReplayOutcome
    data object FailedAppNotInstalled : MockReplayOutcome
    data object FailedMissingRegSec : MockReplayOutcome
    data object FailedNoReceiver : MockReplayOutcome
    data object Failed : MockReplayOutcome
}
