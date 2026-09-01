package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.mipush.runtime.core.PushConnectionRecord
import io.github.magisk317.mipush.runtime.core.PushConnectionState
import io.github.magisk317.mipush.runtime.core.PushReconnectPolicy

/**
 * Updates connection telemetry through the runtime's sole state owner. Host/session ownership
 * remains in [AndroidPushRuntime].
 */
internal class RuntimeConnectionObservationCoordinator(
    private val state: AndroidPushRuntimeState,
) {
    fun observePingSent(atMs: Long) = state.withLock { lastPingSentAtMs = atMs }

    fun observeReadAlive(atMs: Long) = state.withLock { lastReadAliveAtMs = atMs }

    fun observePingTimeout(atMs: Long) = state.withLock { lastPingTimeoutAtMs = atMs }

    fun observeReconnectStarted(atMs: Long) = state.withLock {
        lastReconnectStartedAtMs = atMs
        val disconnectAtMs = maxOf(lastPingTimeoutAtMs, lastDisconnectedAtMs)
        lastDisconnectToReconnectLatencyMs = if (disconnectAtMs > 0L) {
            (atMs - disconnectAtMs).coerceAtLeast(0L)
        } else {
            0L
        }
    }

    fun observeDisconnectReason(reason: Int?) = state.withLock { lastDisconnectReason = reason }

    fun observeReconnectConnected(atMs: Long) = state.withLock {
        lastReconnectConnectedAtMs = atMs
        lastReconnectLatencyMs = if (lastReconnectStartedAtMs > 0L) {
            (atMs - lastReconnectStartedAtMs).coerceAtLeast(0L)
        } else {
            0L
        }
        lastReconnectToConnectedLatencyMs = lastReconnectLatencyMs
    }

    fun applyConnectionState(
        record: PushConnectionRecord,
        nowMs: Long,
        resolvedIp: String? = null,
    ) = state.withLock {
        val previousState = connectionRecord.state
        connectionRecord = record
        when (record.state) {
            PushConnectionState.Connected -> {
                val reconnectElapsedMs = if (lastDisconnectedAtMs > 0L) {
                    (nowMs - lastDisconnectedAtMs).takeIf { it >= 0L }
                } else {
                    null
                }
                val mergesPreviousSession =
                    previousState != PushConnectionState.Connected &&
                        connectionSessionCount > 0L &&
                        connectedAtMs > 0L &&
                        reconnectElapsedMs != null &&
                        reconnectElapsedMs < PushReconnectPolicy.CONNECTION_SESSION_MERGE_WINDOW_MS

                if (previousState != PushConnectionState.Connected && !mergesPreviousSession) {
                    connectedAtMs = nowMs
                    connectionSessionCount += 1
                }
                if (resolvedIp != null) {
                    lastResolvedIp = resolvedIp
                }
            }

            PushConnectionState.Disconnected -> {
                if (previousState != PushConnectionState.Disconnected) {
                    lastDisconnectedAtMs = nowMs
                }
            }

            else -> Unit
        }
    }
}
