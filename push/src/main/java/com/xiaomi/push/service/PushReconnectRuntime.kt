package com.xiaomi.push.service

data class PushReconnectState(
    val attempts: Int,
    val shortLiveConnCount: Int,
    val curDelay: Int,
    val lastConnectTime: Long
)

data class PushReconnectDelayPlan(
    val delayMs: Int,
    val nextState: PushReconnectState
)

enum class PushReconnectAction {
    SkipNoReconnect,
    SkipExistingJob,
    Immediate,
    Delayed
}

data class PushReconnectAttemptPlan(
    val action: PushReconnectAction,
    val delayMs: Int,
    val nextState: PushReconnectState,
    val shouldDumpNativeNetInfo: Boolean,
    val shouldRunConnectivityTest: Boolean,
    val eventAction: String
)

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
                delayMs = MAX_RETRY_INTERVAL,
                nextState = state
            )
        }
        val randomFactor = 1.5
        if (state.attempts > SLOW_RETRY_THRESHOLD) {
            return PushReconnectDelayPlan(
                delayMs = (60000.0 * randomFactor).toInt(),
                nextState = state
            )
        }
        if (state.attempts > 1) {
            return PushReconnectDelayPlan(
                delayMs = (10000.0 * randomFactor).toInt(),
                nextState = state
            )
        }
        if (state.lastConnectTime == 0L) {
            return PushReconnectDelayPlan(delayMs = 0, nextState = state)
        }
        if (nowMs - state.lastConnectTime >= SHORT_LIVE_CONN_THRESHOLD) {
            return PushReconnectDelayPlan(
                delayMs = 0,
                nextState = state.copy(curDelay = 1000, shortLiveConnCount = 0)
            )
        }
        if (state.curDelay >= MAX_RETRY_INTERVAL) {
            return PushReconnectDelayPlan(delayMs = state.curDelay, nextState = state)
        }
        val nextShortLiveCount = state.shortLiveConnCount + 1
        return if (nextShortLiveCount >= SLOW_RETRY_THRESHOLD) {
            PushReconnectDelayPlan(
                delayMs = MAX_RETRY_INTERVAL,
                nextState = state.copy(shortLiveConnCount = nextShortLiveCount)
            )
        } else {
            PushReconnectDelayPlan(
                delayMs = state.curDelay,
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
        shouldReconnect: Boolean,
        hasPendingConnectJob: Boolean,
        nowMs: Long
    ): PushReconnectAttemptPlan {
        if (!shouldReconnect) {
            return PushReconnectAttemptPlan(
                action = PushReconnectAction.SkipNoReconnect,
                delayMs = 0,
                nextState = state,
                shouldDumpNativeNetInfo = false,
                shouldRunConnectivityTest = false,
                eventAction = "reconnect_blocked"
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
            eventAction = if (delayed.delayMs == 0) "reconnect_schedule_now" else "reconnect_schedule_delayed"
        )
    }
}
