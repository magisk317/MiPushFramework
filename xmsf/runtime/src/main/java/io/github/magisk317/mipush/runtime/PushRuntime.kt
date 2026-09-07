package io.github.magisk317.mipush.runtime

import android.content.Intent
import io.github.magisk317.mipush.common.utils.Utils
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
    fun synchronizePersistedRegistrationState(
        androidUserId: Int = currentUserId(),
    ): Int = AndroidPushRuntime.synchronizePersistedRegistrationState(androidUserId)

    @JvmStatic
    fun connectionSnapshot() =
        AndroidPushRuntime.connectionSnapshot()

    @JvmStatic
    fun requestFrameworkRegistration(source: String, reason: String? = null): Boolean =
        AndroidPushRuntime.requestFrameworkRegistration(source, reason)

    @JvmStatic
    fun requestApplicationRegistration(
        packageName: String,
        source: String,
        reason: String? = null,
        androidUserId: Int = currentUserId(),
    ): Boolean = AndroidPushRuntime.requestApplicationRegistration(
        packageName = packageName,
        source = source,
        reason = reason,
        androidUserId = androidUserId,
    )

    @JvmStatic
    fun handleBootCompleted(source: String): PushRuntimeRegistrationDispatchResult =
        AndroidPushRuntime.handleBootCompleted(source)

    @JvmStatic
    fun handleNetworkAvailable(source: String): PushRuntimeRegistrationDispatchResult =
        AndroidPushRuntime.handleNetworkAvailable(source)

    @JvmStatic
    fun requestConnection(source: String, reason: String? = null): Boolean =
        AndroidPushRuntime.requestConnection(source, reason)

    @JvmStatic
    fun requestConnectionReset(source: String, reason: String? = null): Boolean =
        AndroidPushRuntime.requestConnectionReset(source, reason)

    @JvmStatic
    fun observePingSent(atMs: Long) = AndroidPushRuntime.observePingSent(atMs)

    @JvmStatic
    fun observeReadAlive(atMs: Long) = AndroidPushRuntime.observeReadAlive(atMs)

    @JvmStatic
    fun observePingTimeout(atMs: Long) = AndroidPushRuntime.observePingTimeout(atMs)

    @JvmStatic
    fun observeReconnectStarted(atMs: Long) = AndroidPushRuntime.observeReconnectStarted(atMs)

    @JvmStatic
    fun observeDisconnectReason(reason: Int?) = AndroidPushRuntime.observeDisconnectReason(reason)

    @JvmStatic
    fun observeReconnectConnected(atMs: Long) = AndroidPushRuntime.observeReconnectConnected(atMs)

    @JvmStatic
    fun dispatchDownstreamPayload(
        packageName: String?,
        action: String,
        messageId: String?,
        payload: ByteArray,
        source: String,
        launchApp: Boolean,
        androidUserId: Int = io.github.magisk317.mipush.common.utils.Utils.requireValidUserId(
            io.github.magisk317.mipush.common.utils.Utils.myUserId(),
        ),
    ): PushRuntimeApplicationDispatchResult =
        AndroidPushRuntime.dispatchDownstreamPayload(
            packageName,
            action,
            messageId,
            payload,
            source,
            launchApp,
            androidUserId,
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
        nowMs: Long = System.currentTimeMillis(),
        androidUserId: Int = io.github.magisk317.mipush.common.utils.Utils.requireValidUserId(
            io.github.magisk317.mipush.common.utils.Utils.myUserId(),
        )
    ): PushRegistrationRecord =
        AndroidPushRuntime.observeRegistrationRequest(packageName, source, reason, nowMs, androidUserId)

    @JvmStatic
    fun observeRegistrationResult(
        packageName: String,
        success: Boolean,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis(),
        androidUserId: Int = io.github.magisk317.mipush.common.utils.Utils.requireValidUserId(
            io.github.magisk317.mipush.common.utils.Utils.myUserId(),
        )
    ): PushRegistrationRecord =
        AndroidPushRuntime.observeRegistrationResult(packageName, success, source, reason, nowMs, androidUserId)

    @JvmStatic
    fun observeUnregistration(
        packageName: String,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis(),
        androidUserId: Int = io.github.magisk317.mipush.common.utils.Utils.requireValidUserId(
            io.github.magisk317.mipush.common.utils.Utils.myUserId(),
        )
    ): PushRegistrationRecord =
        AndroidPushRuntime.observeUnregistration(packageName, source, reason, nowMs, androidUserId)

    @JvmStatic
    fun observeRegistrationState(
        packageName: String,
        state: PushRegistrationState,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis(),
        androidUserId: Int = io.github.magisk317.mipush.common.utils.Utils.requireValidUserId(
            io.github.magisk317.mipush.common.utils.Utils.myUserId(),
        )
    ): PushRegistrationRecord =
        AndroidPushRuntime.observeRegistrationState(packageName, state, source, reason, nowMs, androidUserId)

    @JvmStatic
    fun getRegistrationRecord(
        packageName: String,
        androidUserId: Int = currentUserId(),
    ): PushRegistrationRecord? = AndroidPushRuntime.getRegistrationRecord(packageName, androidUserId)

    @JvmStatic
    fun getChannelRecords(): List<PushChannelRecord> =
        AndroidPushRuntime.getChannelRecords()

    @JvmStatic
    fun observeInboundMessage(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        isAck: Boolean = false,
        nowMs: Long = System.currentTimeMillis(),
        androidUserId: Int = currentUserId(),
    ): Boolean =
        AndroidPushRuntime.observeInboundMessage(
            packageName,
            action,
            messageId,
            source,
            isAck,
            nowMs,
            androidUserId,
        )

    @JvmStatic
    fun observeTransferToApplication(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        nowMs: Long = System.currentTimeMillis(),
        androidUserId: Int = currentUserId(),
    ) = AndroidPushRuntime.observeTransferToApplication(
        packageName,
        action,
        messageId,
        source,
        nowMs,
        androidUserId,
    )

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
        nowMs: Long = System.currentTimeMillis(),
        androidUserId: Int = currentUserId(),
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
            nowMs,
            androidUserId,
        )

    @JvmStatic
    fun synchronizeChannels(
        connectionState: PushConnectionState,
        host: String?,
        channels: List<PushChannelRecord>,
        source: String,
        nowMs: Long = System.currentTimeMillis(),
        androidUserId: Int = currentUserId(),
    ) = AndroidPushRuntime.synchronizeChannels(
        connectionState,
        host,
        channels,
        source,
        nowMs,
        androidUserId,
    )

    @JvmStatic
    fun observeAccountEvent(action: String, source: String) =
        AndroidPushRuntime.observeAccountEvent(action, source)

    @JvmStatic
    fun capabilities(): PushRuntimeCapabilities =
        AndroidPushRuntime.capabilities()

    @JvmStatic
    fun forceTriggerRegistration(
        packageName: String,
        source: String,
        reason: String? = null,
        androidUserId: Int = currentUserId(),
    ): Boolean = AndroidPushRuntime.forceTriggerRegistration(
        packageName,
        source,
        reason,
        androidUserId,
    )

    @JvmStatic
    fun clearPackageTransientState(packageName: String) =
        AndroidPushRuntime.clearPackageTransientState(packageName)

    @JvmStatic
    fun clearPackageTransientState(packageName: String, userId: Int) =
        AndroidPushRuntime.clearPackageTransientState(packageName, userId)

    private fun currentUserId(): Int = Utils.requireValidUserId(Utils.myUserId())

    @JvmStatic
    fun clearStateForTests() =
        AndroidPushRuntime.clearStateForTests()
}
