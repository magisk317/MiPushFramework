package io.github.magisk317.mipush.runtime

import android.content.Intent

object PushRuntime {
    @JvmStatic
    fun attachBridgeHost(host: PushRuntimeBridgeHost) =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.attachBridgeHost(host)

    @JvmStatic
    fun attachExecutionHost(host: PushRuntimeExecutionHost) =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.attachExecutionHost(host)

    @JvmStatic
    fun detachExecutionHost(host: PushRuntimeExecutionHost) =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.detachExecutionHost(host)

    @JvmStatic
    fun detachBridgeHost(host: PushRuntimeBridgeHost) =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.detachBridgeHost(host)

    @JvmStatic
    fun submitBridgeIntent(intent: Intent) =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.submitBridgeIntent(intent)

    @JvmStatic
    fun snapshot(): PushRuntimeSnapshot =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.snapshot()

    @JvmStatic
    fun requestFrameworkRegistration(source: String, reason: String? = null): Boolean =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.requestFrameworkRegistration(source, reason)

    @JvmStatic
    fun requestApplicationRegistration(packageName: String, source: String, reason: String? = null): Boolean =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.requestApplicationRegistration(packageName, source, reason)

    @JvmStatic
    fun handleBootCompleted(source: String): PushRuntimeRegistrationDispatchResult =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.handleBootCompleted(source)

    @JvmStatic
    fun handleNetworkAvailable(source: String): PushRuntimeRegistrationDispatchResult =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.handleNetworkAvailable(source)

    @JvmStatic
    fun handleAccountChanged(source: String): PushRuntimeRegistrationDispatchResult =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.handleAccountChanged(source)

    @JvmStatic
    fun requestConnection(source: String, reason: String? = null): Boolean =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.requestConnection(source, reason)

    @JvmStatic
    fun requestConnectionReset(source: String, reason: String? = null): Boolean =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.requestConnectionReset(source, reason)

    @JvmStatic
    fun dispatchDownstreamPayload(
        packageName: String?,
        action: String,
        messageId: String?,
        payload: ByteArray,
        source: String,
        launchApp: Boolean
    ): PushRuntimeApplicationDispatchResult =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.dispatchDownstreamPayload(
            packageName,
            action,
            messageId,
            payload,
            source,
            launchApp
        )

    @JvmStatic
    fun cancelNotificationForPayload(
        packageName: String?,
        payload: ByteArray,
        notificationId: Int,
        notificationGroup: String?,
        source: String
    ): Boolean =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.cancelNotificationForPayload(
            packageName,
            payload,
            notificationId,
            notificationGroup,
            source
        )

    @JvmStatic
    fun observeRegistrationRequest(
        packageName: String,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.observeRegistrationRequest(packageName, source, reason, nowMs)

    @JvmStatic
    fun observeRegistrationResult(
        packageName: String,
        success: Boolean,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.observeRegistrationResult(packageName, success, source, reason, nowMs)

    @JvmStatic
    fun observeUnregistration(
        packageName: String,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.observeUnregistration(packageName, source, reason, nowMs)

    @JvmStatic
    fun observeRegistrationState(
        packageName: String,
        state: PushRegistrationState,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.observeRegistrationState(packageName, state, source, reason, nowMs)

    @JvmStatic
    fun observeInboundMessage(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        isAck: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.observeInboundMessage(packageName, action, messageId, source, isAck, nowMs)

    @JvmStatic
    fun observeTransferToApplication(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        nowMs: Long = System.currentTimeMillis()
    ) = io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.observeTransferToApplication(packageName, action, messageId, source, nowMs)

    @JvmStatic
    fun observeNotificationEvent(packageName: String?, action: String, source: String) =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.observeNotificationEvent(packageName, action, source)

    @JvmStatic
    fun observeChannelEvent(packageName: String?, action: String, source: String) =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.observeChannelEvent(packageName, action, source)

    @JvmStatic
    @JvmOverloads
    fun observeConnectionState(
        state: PushConnectionState,
        source: String,
        host: String? = null,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushConnectionRecord =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.observeConnectionState(state, source, host, reason, nowMs)

    @JvmStatic
    fun observeChannelState(
        packageName: String?,
        channelId: String,
        userId: String?,
        session: String?,
        state: PushChannelState,
        source: String,
        reasonCode: Int? = null,
        reasonMessage: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushChannelRecord =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.observeChannelState(
            packageName,
            channelId,
            userId,
            session,
            state,
            source,
            reasonCode,
            reasonMessage,
            nowMs
        )

    @JvmStatic
    fun synchronizeChannels(
        connectionState: PushConnectionState,
        host: String?,
        channels: List<PushChannelRecord>,
        source: String,
        nowMs: Long = System.currentTimeMillis()
    ) = io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.synchronizeChannels(connectionState, host, channels, source, nowMs)

    @JvmStatic
    fun observeAccountEvent(action: String, source: String) =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.observeAccountEvent(action, source)

    @JvmStatic
    fun capabilities(): PushRuntimeCapabilities =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.capabilities()

    @JvmStatic
    fun forceTriggerRegistration(packageName: String, source: String, reason: String? = null): Boolean =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.forceTriggerRegistration(packageName, source, reason)

    @JvmStatic
    fun clearStateForTests() =
        io.github.magisk317.mipush.runtime.android.AndroidPushRuntime.clearStateForTests()
}
