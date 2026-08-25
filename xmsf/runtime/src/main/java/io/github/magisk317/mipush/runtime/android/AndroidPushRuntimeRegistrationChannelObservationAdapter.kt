package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.mipush.runtime.core.PushChannelState
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import io.github.magisk317.mipush.runtime.core.PushRuntimeRegistrationChannelObservationSink

/** Android runtime implementation of the product-owned registration/channel observation contract. */
object AndroidPushRuntimeRegistrationChannelObservationAdapter :
    PushRuntimeRegistrationChannelObservationSink {
    override fun observeRegistrationState(
        packageName: String,
        state: PushRegistrationState,
        source: String,
        reason: String?,
        nowMs: Long,
    ) {
        AndroidPushRuntime.observeRegistrationState(packageName, state, source, reason, nowMs)
    }

    override fun observeRegistrationResult(
        packageName: String,
        success: Boolean,
        source: String,
        reason: String?,
        nowMs: Long,
    ) {
        AndroidPushRuntime.observeRegistrationResult(packageName, success, source, reason, nowMs)
    }

    override fun observeUnregistration(
        packageName: String,
        source: String,
        reason: String?,
        nowMs: Long,
    ) {
        AndroidPushRuntime.observeUnregistration(packageName, source, reason, nowMs)
    }

    override fun observeChannelEvent(
        packageName: String?,
        event: String,
        reason: String,
    ) {
        AndroidPushRuntime.observeChannelEvent(packageName, event, reason)
    }

    override fun observeChannelState(
        packageName: String?,
        channelId: String,
        userId: String?,
        session: String?,
        state: PushChannelState,
        source: String,
        reasonCode: Int?,
        reasonMessage: String?,
        nowMs: Long,
    ) {
        AndroidPushRuntime.observeChannelState(
            packageName,
            channelId,
            userId,
            session,
            state,
            source,
            reasonCode,
            reasonMessage,
            nowMs,
        )
    }
}
