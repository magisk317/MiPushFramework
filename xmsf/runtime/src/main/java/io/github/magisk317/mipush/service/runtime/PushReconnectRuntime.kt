package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.runtime.store.kmp.PushReconnectPolicy
import io.github.magisk317.mipush.runtime.store.kmp.PushReconnectState

/**
 * Android-side adapter delegating to the platform-neutral [PushReconnectPolicy].
 * Kept for source compatibility with existing vendor/runtime callers.
 */
object PushReconnectRuntime {
    @JvmStatic
    fun initialState(): PushReconnectState {
        return PushReconnectPolicy.initialState()
    }

    @JvmStatic
    fun onConnectSucceeded(nowMs: Long): PushReconnectState {
        return PushReconnectPolicy.onConnectSucceeded(nowMs)
    }

    @JvmStatic
    fun computeDelayedReconnect(
        state: PushReconnectState,
        nowMs: Long
    ) = PushReconnectPolicy.computeDelayedReconnect(state, nowMs)

    @JvmStatic
    fun planReconnect(
        state: PushReconnectState,
        forceImmediate: Boolean,
        currentlyConnected: Boolean,
        allowedByPolicy: Boolean,
        hasPendingConnectJob: Boolean,
        nowMs: Long
    ) = PushReconnectPolicy.planReconnect(
        state = state,
        forceImmediate = forceImmediate,
        currentlyConnected = currentlyConnected,
        allowedByPolicy = allowedByPolicy,
        hasPendingConnectJob = hasPendingConnectJob,
        nowMs = nowMs
    )
}
