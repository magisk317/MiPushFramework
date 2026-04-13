package io.github.magisk317.mipush.runtime

import android.content.Intent

object PushRuntime {
    @JvmStatic
    fun attachBridgeHost(host: PushRuntimeBridgeHost) =
        com.xiaomi.xmsf.runtime.PushRuntime.attachBridgeHost(host)

    @JvmStatic
    fun attachExecutionHost(host: PushRuntimeExecutionHost) =
        com.xiaomi.xmsf.runtime.PushRuntime.attachExecutionHost(host)

    @JvmStatic
    fun detachExecutionHost(host: PushRuntimeExecutionHost) =
        com.xiaomi.xmsf.runtime.PushRuntime.detachExecutionHost(host)

    @JvmStatic
    fun detachBridgeHost(host: PushRuntimeBridgeHost) =
        com.xiaomi.xmsf.runtime.PushRuntime.detachBridgeHost(host)

    @JvmStatic
    fun submitBridgeIntent(intent: Intent) =
        com.xiaomi.xmsf.runtime.PushRuntime.submitBridgeIntent(intent)

    @JvmStatic
    fun snapshot(): PushRuntimeSnapshot =
        com.xiaomi.xmsf.runtime.PushRuntime.snapshot()

    @JvmStatic
    fun requestFrameworkRegistration(source: String, reason: String? = null): Boolean =
        com.xiaomi.xmsf.runtime.PushRuntime.requestFrameworkRegistration(source, reason)

    @JvmStatic
    fun requestApplicationRegistration(packageName: String, source: String, reason: String? = null): Boolean =
        com.xiaomi.xmsf.runtime.PushRuntime.requestApplicationRegistration(packageName, source, reason)

    @JvmStatic
    fun handleBootCompleted(source: String): PushRuntimeRegistrationDispatchResult =
        com.xiaomi.xmsf.runtime.PushRuntime.handleBootCompleted(source)

    @JvmStatic
    fun handleNetworkAvailable(source: String): PushRuntimeRegistrationDispatchResult =
        com.xiaomi.xmsf.runtime.PushRuntime.handleNetworkAvailable(source)

    @JvmStatic
    fun handleAccountChanged(source: String): PushRuntimeRegistrationDispatchResult =
        com.xiaomi.xmsf.runtime.PushRuntime.handleAccountChanged(source)

    @JvmStatic
    fun requestConnection(source: String, reason: String? = null): Boolean =
        com.xiaomi.xmsf.runtime.PushRuntime.requestConnection(source, reason)

    @JvmStatic
    fun requestConnectionReset(source: String, reason: String? = null): Boolean =
        com.xiaomi.xmsf.runtime.PushRuntime.requestConnectionReset(source, reason)

    @JvmStatic
    fun dispatchDownstreamPayload(
        packageName: String?,
        action: String,
        messageId: String?,
        payload: ByteArray,
        source: String,
        launchApp: Boolean
    ): PushRuntimeApplicationDispatchResult =
        com.xiaomi.xmsf.runtime.PushRuntime.dispatchDownstreamPayload(
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
        com.xiaomi.xmsf.runtime.PushRuntime.cancelNotificationForPayload(
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
        com.xiaomi.xmsf.runtime.PushRuntime.observeRegistrationRequest(packageName, source, reason, nowMs)

    @JvmStatic
    fun observeRegistrationResult(
        packageName: String,
        success: Boolean,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        com.xiaomi.xmsf.runtime.PushRuntime.observeRegistrationResult(packageName, success, source, reason, nowMs)

    @JvmStatic
    fun observeUnregistration(
        packageName: String,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        com.xiaomi.xmsf.runtime.PushRuntime.observeUnregistration(packageName, source, reason, nowMs)

    @JvmStatic
    fun observeRegistrationState(
        packageName: String,
        state: PushRegistrationState,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord =
        com.xiaomi.xmsf.runtime.PushRuntime.observeRegistrationState(packageName, state, source, reason, nowMs)

    @JvmStatic
    fun observeInboundMessage(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        isAck: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean =
        com.xiaomi.xmsf.runtime.PushRuntime.observeInboundMessage(packageName, action, messageId, source, isAck, nowMs)

    @JvmStatic
    fun observeTransferToApplication(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        nowMs: Long = System.currentTimeMillis()
    ) = com.xiaomi.xmsf.runtime.PushRuntime.observeTransferToApplication(packageName, action, messageId, source, nowMs)

    @JvmStatic
    fun observeNotificationEvent(packageName: String?, action: String, source: String) =
        com.xiaomi.xmsf.runtime.PushRuntime.observeNotificationEvent(packageName, action, source)

    @JvmStatic
    fun observeChannelEvent(packageName: String?, action: String, source: String) =
        com.xiaomi.xmsf.runtime.PushRuntime.observeChannelEvent(packageName, action, source)

    @JvmStatic
    fun observeConnectionState(
        state: PushConnectionState,
        source: String,
        host: String? = null,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushConnectionRecord =
        com.xiaomi.xmsf.runtime.PushRuntime.observeConnectionState(state, source, host, reason, nowMs)

    @JvmStatic
    fun observeConnectionState(
        state: com.xiaomi.push.service.PushConnectionState,
        source: String,
        host: String?,
        reason: String?
    ): PushConnectionRecord =
        observeConnectionState(
            state = when (state) {
                com.xiaomi.push.service.PushConnectionState.Connected -> PushConnectionState.Connected
                com.xiaomi.push.service.PushConnectionState.Connecting -> PushConnectionState.Connecting
                com.xiaomi.push.service.PushConnectionState.Disconnected,
                com.xiaomi.push.service.PushConnectionState.Disconnecting -> PushConnectionState.Disconnected
            },
            source = source,
            host = host,
            reason = reason
        )

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
        com.xiaomi.xmsf.runtime.PushRuntime.observeChannelState(
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
    ) = com.xiaomi.xmsf.runtime.PushRuntime.synchronizeChannels(connectionState, host, channels, source, nowMs)

    @JvmStatic
    fun observeAccountEvent(action: String, source: String) =
        com.xiaomi.xmsf.runtime.PushRuntime.observeAccountEvent(action, source)

    @JvmStatic
    fun capabilities(): PushRuntimeCapabilities =
        com.xiaomi.xmsf.runtime.PushRuntime.capabilities()

    @JvmStatic
    fun clearStateForTests() =
        com.xiaomi.xmsf.runtime.PushRuntime.clearStateForTests()
}
