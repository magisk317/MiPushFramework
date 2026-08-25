package io.github.magisk317.mipush.runtime.core

/**
 * Product-owned sink for registration and channel observations emitted by the stock push observer.
 *
 * The vendor observer remains the ingress surface; payload repair, pending work, callbacks, and
 * channel synchronization stay outside this state-recording contract.
 */
interface PushRuntimeRegistrationChannelObservationSink {
    fun observeRegistrationState(
        packageName: String,
        state: PushRegistrationState,
        source: String,
        reason: String?,
        nowMs: Long,
    )

    fun observeRegistrationResult(
        packageName: String,
        success: Boolean,
        source: String,
        reason: String?,
        nowMs: Long,
    )

    fun observeUnregistration(
        packageName: String,
        source: String,
        reason: String?,
        nowMs: Long,
    )

    fun observeChannelEvent(
        packageName: String?,
        event: String,
        reason: String,
    )

    fun observeChannelState(
        packageName: String?,
        channelId: String,
        userId: String?,
        session: String?,
        state: PushChannelState,
        source: String,
        reasonCode: Int?,
        reasonMessage: String?,
        nowMs: Long,
    )
}
