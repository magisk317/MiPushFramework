package io.github.magisk317.mipush.runtime

import android.content.Intent
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntime

object PushRuntime {
    @JvmStatic
    fun attachBridgeHost(host: PushRuntimeBridgeHost) =
        AndroidPushRuntime.attachBridgeHost(host)

    @JvmStatic
    fun attachExecutionHost(host: PushRuntimeExecutionHost) =
        AndroidPushRuntime.attachExecutionHost(host)

    @JvmStatic
    fun detachExecutionHost(host: PushRuntimeExecutionHost) =
        AndroidPushRuntime.detachExecutionHost(host)

    @JvmStatic
    fun detachBridgeHost(host: PushRuntimeBridgeHost) =
        AndroidPushRuntime.detachBridgeHost(host)

    @JvmStatic
    fun submitBridgeIntent(intent: Intent) =
        AndroidPushRuntime.submitBridgeIntent(intent)

    @JvmStatic
    fun snapshot(): PushRuntimeSnapshot =
        AndroidPushRuntime.snapshot()

    @JvmStatic
    fun requestFrameworkRegistration(source: String, reason: String? = null): Boolean =
        AndroidPushRuntime.requestFrameworkRegistration(source, reason)

    @JvmStatic
    fun requestApplicationRegistration(packageName: String, source: String, reason: String? = null): Boolean =
        AndroidPushRuntime.requestApplicationRegistration(packageName, source, reason)

    @JvmStatic
    fun handleBootCompleted(source: String): PushRuntimeRegistrationDispatchResult =
        AndroidPushRuntime.handleBootCompleted(source)

    @JvmStatic
    fun handleNetworkAvailable(source: String): PushRuntimeRegistrationDispatchResult =
        AndroidPushRuntime.handleNetworkAvailable(source)

    @JvmStatic
    fun handleAccountChanged(source: String): PushRuntimeRegistrationDispatchResult =
        AndroidPushRuntime.handleAccountChanged(source)

    @JvmStatic
    fun requestConnection(source: String, reason: String? = null): Boolean =
        AndroidPushRuntime.requestConnection(source, reason)

    @JvmStatic
    fun requestConnectionReset(source: String, reason: String? = null): Boolean =
        AndroidPushRuntime.requestConnectionReset(source, reason)

    @JvmStatic
    fun dispatchDownstreamPayload(
        packageName: String?,
        action: String,
        messageId: String?,
        payload: ByteArray,
        source: String,
        launchApp: Boolean
    ): PushRuntimeApplicationDispatchResult =
        AndroidPushRuntime.dispatchDownstreamPayload(
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
        AndroidPushRuntime.cancelNotificationForPayload(
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
        AndroidPushRuntime.observeRegistrationRequest(packageName, source, reason, nowMs)

    @JvmStatic
    fun observeRegistrationResult(
        packageName: String,
        success: Boolean,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        AndroidPushRuntime.observeRegistrationResult(packageName, success, source, reason, nowMs)

    @JvmStatic
    fun observeUnregistration(
        packageName: String,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        AndroidPushRuntime.observeUnregistration(packageName, source, reason, nowMs)

    @JvmStatic
    fun observeRegistrationState(
        packageName: String,
        state: PushRegistrationState,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        AndroidPushRuntime.observeRegistrationState(packageName, state, source, reason, nowMs)

    @JvmStatic
    fun observeInboundMessage(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        isAck: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean =
        AndroidPushRuntime.observeInboundMessage(packageName, action, messageId, source, isAck, nowMs)

    @JvmStatic
    fun observeTransferToApplication(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        nowMs: Long = System.currentTimeMillis()
    ) = AndroidPushRuntime.observeTransferToApplication(packageName, action, messageId, source, nowMs)

    @JvmStatic
    fun observeNotificationEvent(packageName: String?, action: String, source: String) =
        AndroidPushRuntime.observeNotificationEvent(packageName, action, source)

    @JvmStatic
    fun observeChannelEvent(packageName: String?, action: String, source: String) =
        AndroidPushRuntime.observeChannelEvent(packageName, action, source)

    @JvmStatic
    @JvmOverloads
    fun observeConnectionState(
        state: PushConnectionState,
        source: String,
        host: String? = null,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushConnectionRecord =
        AndroidPushRuntime.observeConnectionState(state, source, host, reason, nowMs)

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
        AndroidPushRuntime.observeChannelState(
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
    ) = AndroidPushRuntime.synchronizeChannels(connectionState, host, channels, source, nowMs)

    @JvmStatic
    fun observeAccountEvent(action: String, source: String) =
        AndroidPushRuntime.observeAccountEvent(action, source)

    @JvmStatic
    fun capabilities(): PushRuntimeCapabilities =
        AndroidPushRuntime.capabilities()

    @JvmStatic
    fun forceTriggerRegistration(packageName: String, source: String, reason: String? = null): Boolean =
        AndroidPushRuntime.forceTriggerRegistration(packageName, source, reason)

    @JvmStatic
    fun clearStateForTests() =
        AndroidPushRuntime.clearStateForTests()
}
