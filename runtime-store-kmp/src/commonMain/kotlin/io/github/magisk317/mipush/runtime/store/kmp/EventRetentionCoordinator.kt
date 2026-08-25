/**
 * Platform-neutral state transitions for event-retention pruning.
 *
 * The Android adapter owns persistence, clocks, logging, and atomic publication of the state;
 * this coordinator owns only the decision and immutable state transition.
 */
package io.github.magisk317.mipush.runtime.store.kmp

data class EventRetentionState(
    val lastPruneAtMillis: Long = 0L,
    val pruneInProgress: Boolean = false,
)

data class EventRetentionStart(
    val retentionDays: Int,
    val state: EventRetentionState,
)

object EventRetentionCoordinator {
    fun beginNow(
        state: EventRetentionState,
        nowMillis: Long,
        retentionDays: Int,
    ): EventRetentionStart? =
        if (state.pruneInProgress) {
            null
        } else {
            EventRetentionStart(
                retentionDays = retentionDays,
                state = state.copy(
                    lastPruneAtMillis = nowMillis,
                    pruneInProgress = true,
                ),
            )
        }

    fun beginMaybe(
        state: EventRetentionState,
        nowMillis: Long,
        retentionDays: Int,
    ): EventRetentionStart? =
        if (!EventRetentionPolicy.shouldPrune(
                nowMillis = nowMillis,
                lastPruneAtMillis = state.lastPruneAtMillis,
                pruneInProgress = state.pruneInProgress,
            )
        ) {
            null
        } else {
            beginNow(state, nowMillis, retentionDays)
        }

    fun finish(state: EventRetentionState): EventRetentionState =
        state.copy(pruneInProgress = false)
}
