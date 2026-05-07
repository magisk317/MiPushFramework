package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.*

object PushReconnectRuntime {
    private const val MAX_RETRY_INTERVAL = 300000
    private const val SHORT_LIVE_CONN_THRESHOLD = 310000L
    private const val SLOW_RETRY_THRESHOLD = 4

    @JvmStatic
    fun initialState(): PushReconnectState {
        return PushReconnectState(
            attempts = 0,
            shortLiveConnCount = 0,
            curDelay = 500,
            lastConnectTime = 0L
        )
    }

    @JvmStatic
    fun onConnectSucceeded(nowMs: Long): PushReconnectState {
        return PushReconnectState(
            attempts = 0,
            shortLiveConnCount = 0,
            curDelay = 500,
            lastConnectTime = nowMs
        )
    }

    @JvmStatic
    fun computeDelayedReconnect(state: PushReconnectState, nowMs: Long): PushReconnectDelayPlan {
        if (state.attempts > 8) {
            return PushReconnectDelayPlan(
                delayMs = MAX_RETRY_INTERVAL.toLong(),
                nextState = state
            )
        }
        val randomFactor = 1.5
        if (state.attempts > SLOW_RETRY_THRESHOLD) {
            return PushReconnectDelayPlan(
                delayMs = (60000.0 * randomFactor).toLong(),
                nextState = state
            )
        }
        if (state.attempts > 1) {
            return PushReconnectDelayPlan(
                delayMs = (10000.0 * randomFactor).toLong(),
                nextState = state
            )
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

    @JvmStatic
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
                shouldDumpNativeNetInfo = false,
                shouldRunConnectivityTest = false,
                eventAction = "reconnect_blocked_by_policy"
            )
        }

        if (currentlyConnected && !forceImmediate) {
            return PushReconnectAttemptPlan(
                action = PushReconnectAction.SkipNoReconnect,
                delayMs = 0,
                nextState = state.copy(attempts = 0), // Reset attempts if already connected
                shouldDumpNativeNetInfo = false,
                shouldRunConnectivityTest = false,
                eventAction = "reconnect_skipped_already_connected"
            )
        }

        if (forceImmediate) {
            return PushReconnectAttemptPlan(
                action = PushReconnectAction.Immediate,
                delayMs = 0,
                nextState = if (hasPendingConnectJob) state else state.copy(attempts = state.attempts + 1),
                shouldDumpNativeNetInfo = false,
                shouldRunConnectivityTest = false,
                eventAction = if (hasPendingConnectJob) "reconnect_immediate_replace" else "reconnect_immediate"
            )
        }
        if (hasPendingConnectJob) {
            return PushReconnectAttemptPlan(
                action = PushReconnectAction.SkipExistingJob,
                delayMs = 0,
                nextState = state,
                shouldDumpNativeNetInfo = false,
                shouldRunConnectivityTest = false,
                eventAction = "reconnect_already_scheduled"
            )
        }

        val delayed = computeDelayedReconnect(state, nowMs)
        val nextState = delayed.nextState.copy(attempts = delayed.nextState.attempts + 1)
        return PushReconnectAttemptPlan(
            action = PushReconnectAction.Delayed,
            delayMs = delayed.delayMs,
            nextState = nextState,
            shouldDumpNativeNetInfo = nextState.attempts == 2,
            shouldRunConnectivityTest = nextState.attempts == 3,
            eventAction = if (delayed.delayMs == 0L) "reconnect_schedule_now" else "reconnect_schedule_delayed"
        )
    }
}
