package io.github.magisk317.mipush.runtime.core

/**
 * Platform-neutral reconnect state machine.
 *
 * The Android adapter owns the actual connection scheduling, logging, and vendor SDK calls;
 * this policy owns only the decision logic for reconnect delay and attempt planning.
 */
data class PushReconnectState(
    val attempts: Int = 0,
    val shortLiveConnCount: Int = 0,
    val curDelay: Int = 500,
    val lastConnectTime: Long = 0L
)

data class PushReconnectDelayPlan(
    val delayMs: Long,
    val nextState: PushReconnectState,
    val eventAction: String = ""
)

enum class PushReconnectAction {
    SkipNoReconnect,
    SkipExistingJob,
    Immediate,
    Schedule,
    Skip,
    Delayed
}

data class PushReconnectAttemptPlan(
    val shouldAttempt: Boolean = true,
    val action: PushReconnectAction = PushReconnectAction.Skip,
    val nextState: PushReconnectState = PushReconnectState(),
    val eventAction: String,
    val delayMs: Long = 0,
    val shouldDumpNativeNetInfo: Boolean = false,
    val shouldRunConnectivityTest: Boolean = false
)

object PushReconnectPolicy {
    private const val MAX_RETRY_INTERVAL = 300000
    /**
     * A reconnect inside this window is treated as the same logical connection session.
     * Keep this aligned with the existing short-lived connection retry boundary.
     */
    const val CONNECTION_SESSION_MERGE_WINDOW_MS = 310000L
    private const val SHORT_LIVE_CONN_THRESHOLD = CONNECTION_SESSION_MERGE_WINDOW_MS
    private const val SLOW_RETRY_THRESHOLD = 4

    fun initialState(): PushReconnectState {
        return PushReconnectState()
    }

    fun onConnectSucceeded(nowMs: Long): PushReconnectState {
        return PushReconnectState(lastConnectTime = nowMs)
    }

    fun computeDelayedReconnect(state: PushReconnectState, nowMs: Long): PushReconnectDelayPlan {
        if (state.attempts > 8) {
            return PushReconnectDelayPlan(delayMs = MAX_RETRY_INTERVAL.toLong(), nextState = state)
        }
        if (state.attempts > SLOW_RETRY_THRESHOLD) {
            return PushReconnectDelayPlan(delayMs = 90000L, nextState = state)
        }
        if (state.attempts > 1) {
            return PushReconnectDelayPlan(delayMs = 15000L, nextState = state)
        }
        if (state.lastConnectTime == 0L) {
            return PushReconnectDelayPlan(delayMs = 0L, nextState = state)
        }
        if (nowMs - state.lastConnectTime >= SHORT_LIVE_CONN_THRESHOLD) {
            return PushReconnectDelayPlan(
                delayMs = 0L,
                nextState = state.copy(curDelay = 1000, shortLiveConnCount = 0)
            )
        }
        if (state.curDelay >= MAX_RETRY_INTERVAL) {
            return PushReconnectDelayPlan(delayMs = state.curDelay.toLong(), nextState = state)
        }
        val nextShortLiveCount = state.shortLiveConnCount + 1
        return if (nextShortLiveCount >= SLOW_RETRY_THRESHOLD) {
            PushReconnectDelayPlan(
                delayMs = MAX_RETRY_INTERVAL.toLong(),
                nextState = state.copy(shortLiveConnCount = nextShortLiveCount)
            )
        } else {
            PushReconnectDelayPlan(
                delayMs = state.curDelay.toLong(),
                nextState = state.copy(
                    shortLiveConnCount = nextShortLiveCount,
                    curDelay = (state.curDelay * 1.5).toInt()
                )
            )
        }
    }

    fun planReconnect(
        state: PushReconnectState,
        forceImmediate: Boolean,
        currentlyConnected: Boolean,
        allowedByPolicy: Boolean,
        hasPendingConnectJob: Boolean,
        nowMs: Long
    ): PushReconnectAttemptPlan {
        if (!allowedByPolicy) {
            return PushReconnectAttemptPlan(
                action = PushReconnectAction.SkipNoReconnect,
                delayMs = 0,
                nextState = state,
                eventAction = "reconnect_blocked_by_policy"
            )
        }
        if (currentlyConnected && !forceImmediate) {
            return PushReconnectAttemptPlan(
                action = PushReconnectAction.SkipNoReconnect,
                delayMs = 0,
                nextState = state.copy(attempts = 0),
                eventAction = "reconnect_skipped_already_connected"
            )
        }
        if (forceImmediate) {
            return PushReconnectAttemptPlan(
                action = PushReconnectAction.Immediate,
                delayMs = 0,
                nextState = if (hasPendingConnectJob) state else state.copy(attempts = state.attempts + 1),
                eventAction = if (hasPendingConnectJob) "reconnect_immediate_replace" else "reconnect_immediate"
            )
        }
        if (hasPendingConnectJob) {
            return PushReconnectAttemptPlan(
                action = PushReconnectAction.SkipExistingJob,
                delayMs = 0,
                nextState = state,
                eventAction = "reconnect_already_scheduled"
            )
        }
        val delayed = computeDelayedReconnect(state, nowMs)
        val nextState = delayed.nextState.copy(attempts = delayed.nextState.attempts + 1)
        return PushReconnectAttemptPlan(
            action = PushReconnectAction.Schedule,
            delayMs = delayed.delayMs,
            nextState = nextState,
            shouldDumpNativeNetInfo = nextState.attempts == 2,
            shouldRunConnectivityTest = nextState.attempts == 3,
            eventAction = if (delayed.delayMs == 0L) "reconnect_schedule_now" else "reconnect_schedule_delayed"
        )
    }
}
