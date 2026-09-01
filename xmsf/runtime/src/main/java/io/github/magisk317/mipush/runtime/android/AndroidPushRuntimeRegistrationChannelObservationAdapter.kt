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
        androidUserId: Int,
    ) {
        AndroidPushRuntime.observeRegistrationState(packageName, state, source, reason, nowMs, androidUserId)
    }

    override fun observeRegistrationResult(
        packageName: String,
        success: Boolean,
        source: String,
        reason: String?,
        nowMs: Long,
        androidUserId: Int,
    ) {
        AndroidPushRuntime.observeRegistrationResult(packageName, success, source, reason, nowMs, androidUserId)
    }

    override fun observeUnregistration(
        packageName: String,
        source: String,
        reason: String?,
        nowMs: Long,
        androidUserId: Int,
    ) {
        AndroidPushRuntime.observeUnregistration(packageName, source, reason, nowMs, androidUserId)
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
        androidUserId: Int,
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
            androidUserId,
        )
    }
}
