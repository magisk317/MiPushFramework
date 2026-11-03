package io.github.magisk317.mipush.manager.application

/**
 * Observable result of replaying a stored push notification.
 *
 * [Dispatched] means that the payload entered a legacy/runtime path whose final notification post
 * cannot be observed synchronously. [PostedTarget] confirms target-package ownership, while
 * [PostedLocalFallback] confirms that XMSF displayed the notification as a degraded fallback.
 */
sealed interface MockReplayOutcome {
    data object BlockedByPermission : MockReplayOutcome
    data object Dispatched : MockReplayOutcome
    data object PostedTarget : MockReplayOutcome
    data object PostedLocalFallback : MockReplayOutcome
    data object FailedChannelDisabled : MockReplayOutcome
    data object FailedEventNotFound : MockReplayOutcome
    data object FailedPayloadMissing : MockReplayOutcome
    data object FailedServiceNotReady : MockReplayOutcome
    data object FailedAppNotInstalled : MockReplayOutcome
    data object FailedMissingRegSec : MockReplayOutcome
    data object FailedNoReceiver : MockReplayOutcome
    data object Failed : MockReplayOutcome
}
