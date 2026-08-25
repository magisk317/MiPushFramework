package io.github.magisk317.mipush.runtime.core

/**
 * Product-owned sink for connection telemetry emitted by the stock push observer.
 *
 * The vendor observer remains the ingress surface, while this contract keeps runtime state
 * recording independent from shell lifecycle and vendor implementation details.
 */
interface PushRuntimeObservationSink {
    fun observePingSent(atMs: Long)

    fun observeReadAlive(atMs: Long)

    fun observePingTimeout(atMs: Long)

    fun observeReconnectStarted(atMs: Long)

    fun observeDisconnectReason(reason: Int?)

    fun observeReconnectConnected(atMs: Long)

    fun observeConnectionState(
        state: PushConnectionState,
        source: String,
        host: String? = null,
        reason: String? = null,
        nowMs: Long,
    )
}
