package io.github.magisk317.mipush.common.notification

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
    data object Failed : MockReplayOutcome
}
