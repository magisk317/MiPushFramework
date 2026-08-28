package io.github.magisk317.mipush.runtime.android

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
}
