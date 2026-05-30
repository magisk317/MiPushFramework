package io.github.magisk317.mipush.runtime

import android.content.Intent

object PushRuntime {
    @JvmStatic
    fun attachBridgeHost(host: PushRuntimeBridgeHost) =
        io.github.magisk317.mipush.runtime.android.PushRuntime.attachBridgeHost(host)

    @JvmStatic
    fun attachExecutionHost(host: PushRuntimeExecutionHost) =
        io.github.magisk317.mipush.runtime.android.PushRuntime.attachExecutionHost(host)

    @JvmStatic
    fun detachExecutionHost(host: PushRuntimeExecutionHost) =
        io.github.magisk317.mipush.runtime.android.PushRuntime.detachExecutionHost(host)

    @JvmStatic
    fun detachBridgeHost(host: PushRuntimeBridgeHost) =
        io.github.magisk317.mipush.runtime.android.PushRuntime.detachBridgeHost(host)

    @JvmStatic
    fun submitBridgeIntent(intent: Intent) =
        io.github.magisk317.mipush.runtime.android.PushRuntime.submitBridgeIntent(intent)

    @JvmStatic
    fun snapshot(): PushRuntimeSnapshot =
        io.github.magisk317.mipush.runtime.android.PushRuntime.snapshot()

    @JvmStatic
    fun requestFrameworkRegistration(source: String, reason: String? = null): Boolean =
        io.github.magisk317.mipush.runtime.android.PushRuntime.requestFrameworkRegistration(source, reason)

    @JvmStatic
    fun requestApplicationRegistration(packageName: String, source: String, reason: String? = null): Boolean =
        io.github.magisk317.mipush.runtime.android.PushRuntime.requestApplicationRegistration(packageName, source, reason)

    @JvmStatic
    fun handleBootCompleted(source: String): PushRuntimeRegistrationDispatchResult =
        io.github.magisk317.mipush.runtime.android.PushRuntime.handleBootCompleted(source)

    @JvmStatic
    fun handleNetworkAvailable(source: String): PushRuntimeRegistrationDispatchResult =
        io.github.magisk317.mipush.runtime.android.PushRuntime.handleNetworkAvailable(source)

    @JvmStatic
    fun handleAccountChanged(source: String): PushRuntimeRegistrationDispatchResult =
        io.github.magisk317.mipush.runtime.android.PushRuntime.handleAccountChanged(source)

    @JvmStatic
    fun requestConnection(source: String, reason: String? = null): Boolean =
        io.github.magisk317.mipush.runtime.android.PushRuntime.requestConnection(source, reason)

    @JvmStatic
    fun requestConnectionReset(source: String, reason: String? = null): Boolean =
        io.github.magisk317.mipush.runtime.android.PushRuntime.requestConnectionReset(source, reason)

    @JvmStatic
    fun dispatchDownstreamPayload(
        packageName: String?,
        action: String,
        messageId: String?,
        payload: ByteArray,
        source: String,
        launchApp: Boolean
    ): PushRuntimeApplicationDispatchResult =
        io.github.magisk317.mipush.runtime.android.PushRuntime.dispatchDownstreamPayload(
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
        io.github.magisk317.mipush.runtime.android.PushRuntime.cancelNotificationForPayload(
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
        io.github.magisk317.mipush.runtime.android.PushRuntime.observeRegistrationRequest(packageName, source, reason, nowMs)

    @JvmStatic
    fun observeRegistrationResult(
        packageName: String,
        success: Boolean,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        io.github.magisk317.mipush.runtime.android.PushRuntime.observeRegistrationResult(packageName, success, source, reason, nowMs)

    @JvmStatic
    fun observeUnregistration(
        packageName: String,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        io.github.magisk317.mipush.runtime.android.PushRuntime.observeUnregistration(packageName, source, reason, nowMs)

    @JvmStatic
    fun observeRegistrationState(
        packageName: String,
        state: PushRegistrationState,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        io.github.magisk317.mipush.runtime.android.PushRuntime.observeRegistrationState(packageName, state, source, reason, nowMs)

    @JvmStatic
    fun observeInboundMessage(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        isAck: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean =
        io.github.magisk317.mipush.runtime.android.PushRuntime.observeInboundMessage(packageName, action, messageId, source, isAck, nowMs)

    @JvmStatic
    fun observeTransferToApplication(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        nowMs: Long = System.currentTimeMillis()
    ) = io.github.magisk317.mipush.runtime.android.PushRuntime.observeTransferToApplication(packageName, action, messageId, source, nowMs)

    @JvmStatic
    fun observeNotificationEvent(packageName: String?, action: String, source: String) =
        io.github.magisk317.mipush.runtime.android.PushRuntime.observeNotificationEvent(packageName, action, source)

    @JvmStatic
    fun observeChannelEvent(packageName: String?, action: String, source: String) =
        io.github.magisk317.mipush.runtime.android.PushRuntime.observeChannelEvent(packageName, action, source)

    @JvmStatic
    @JvmOverloads
    fun observeConnectionState(
        state: PushConnectionState,
        source: String,
        host: String? = null,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushConnectionRecord =
        io.github.magisk317.mipush.runtime.android.PushRuntime.observeConnectionState(state, source, host, reason, nowMs)

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
        io.github.magisk317.mipush.runtime.android.PushRuntime.observeChannelState(
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
    ) = io.github.magisk317.mipush.runtime.android.PushRuntime.synchronizeChannels(connectionState, host, channels, source, nowMs)

    @JvmStatic
    fun observeAccountEvent(action: String, source: String) =
        io.github.magisk317.mipush.runtime.android.PushRuntime.observeAccountEvent(action, source)

    @JvmStatic
    fun capabilities(): PushRuntimeCapabilities =
        io.github.magisk317.mipush.runtime.android.PushRuntime.capabilities()

    @JvmStatic
    fun forceTriggerRegistration(packageName: String, source: String, reason: String? = null): Boolean =
        io.github.magisk317.mipush.runtime.android.PushRuntime.forceTriggerRegistration(packageName, source, reason)

    @JvmStatic
    fun clearStateForTests() =
        io.github.magisk317.mipush.runtime.android.PushRuntime.clearStateForTests()
}
