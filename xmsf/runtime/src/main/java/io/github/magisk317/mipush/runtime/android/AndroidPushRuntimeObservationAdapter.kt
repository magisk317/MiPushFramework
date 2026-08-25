package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.mipush.runtime.core.PushConnectionState
import io.github.magisk317.mipush.runtime.core.PushRuntimeObservationSink

/** Android runtime implementation of the product-owned connection observation contract. */
object AndroidPushRuntimeObservationAdapter : PushRuntimeObservationSink {
    override fun observePingSent(atMs: Long) = AndroidPushRuntime.observePingSent(atMs)

    override fun observeReadAlive(atMs: Long) = AndroidPushRuntime.observeReadAlive(atMs)

    override fun observePingTimeout(atMs: Long) = AndroidPushRuntime.observePingTimeout(atMs)

    override fun observeReconnectStarted(atMs: Long) = AndroidPushRuntime.observeReconnectStarted(atMs)

    override fun observeDisconnectReason(reason: Int?) = AndroidPushRuntime.observeDisconnectReason(reason)

    override fun observeReconnectConnected(atMs: Long) = AndroidPushRuntime.observeReconnectConnected(atMs)

    override fun observeConnectionState(
        state: PushConnectionState,
        source: String,
        host: String?,
        reason: String?,
        nowMs: Long,
    ) {
        AndroidPushRuntime.observeConnectionState(state, source, host, reason, nowMs)
    }
}
