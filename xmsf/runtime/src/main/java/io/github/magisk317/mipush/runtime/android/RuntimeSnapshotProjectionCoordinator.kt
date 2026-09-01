package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.core.PUSH_RUNTIME_API_VERSION
import io.github.magisk317.mipush.runtime.core.PushChannelRecord
import io.github.magisk317.mipush.runtime.core.PushChannelState
import io.github.magisk317.mipush.runtime.core.PushRegistrationRecord
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import io.github.magisk317.mipush.runtime.core.PushRuntimeCapability

/**
 * Builds coherent runtime read models through the sole [AndroidPushRuntimeState] lock.
 * It deliberately owns no host, Binder, registration, or channel lifecycle behavior.
 */
internal class RuntimeSnapshotProjectionCoordinator(
    private val state: AndroidPushRuntimeState,
) {
    fun snapshot(): PushRuntimeSnapshot = state.withLock {
        PushRuntimeSnapshot(
            bridgeReady = bridgeHost != null,
            executionReady = executionHost != null,
            pendingBridgeIntentCount = pendingBridgeIntents.size(),
            connectionState = connectionRecord.state,
            trackedChannelCount = channelRecords.size,
            boundChannelCount = channelRecords.values.count { it.state == PushChannelState.Bound },
            trackedRegistrationCount = registrationRecords.size,
            registeredPackageCount = registrationRecords.values.count {
                it.state == PushRegistrationState.Registered
            },
            downstreamMessageCount = downstreamMessageCount,
            deliveredToAppCount = deliveredToAppCount,
            duplicateMessageCount = duplicateMessageCount,
            ackMessageCount = ackMessageCount,
            broadcastFallbackDeliveryCount = broadcastFallbackDeliveryCount,
            notificationCancelCount = notificationCancelCount,
            notificationEventCount = notificationEventCount,
            channelEventCount = channelEventCount,
            accountEventCount = accountEventCount,
            lastPackageName = lastPackageName,
            lastAction = lastAction,
            lastChannelPackage = lastChannelPackage,
            lastChannelState = lastChannelState,
            lastRegistrationPackage = lastRegistrationPackage,
            lastRegistrationState = lastRegistrationState,
        )
    }

    fun connectionSnapshot(): AndroidPushRuntime.ConnectionSnapshotData = state.withLock {
        AndroidPushRuntime.ConnectionSnapshotData(
            connectionState = connectionRecord.state.name,
            connectedAtMs = connectedAtMs,
            lastDisconnectedAtMs = lastDisconnectedAtMs,
            connectionSessionCount = connectionSessionCount,
            serverHost = connectionRecord.host,
            resolvedIp = lastResolvedIp,
            downstreamMessageCount = downstreamMessageCount,
            deliveredToAppCount = deliveredToAppCount,
            duplicateMessageCount = duplicateMessageCount,
            ackMessageCount = ackMessageCount,
            registeredPackageCount = registrationRecords.values.count {
                it.state == PushRegistrationState.Registered
            },
            trackedChannelCount = channelRecords.size,
            boundChannelCount = channelRecords.values.count { it.state == PushChannelState.Bound },
            lastPingSentAtMs = lastPingSentAtMs,
            lastReadAliveAtMs = lastReadAliveAtMs,
            lastPingTimeoutAtMs = lastPingTimeoutAtMs,
            lastDisconnectReason = lastDisconnectReason,
            lastReconnectStartedAtMs = lastReconnectStartedAtMs,
            lastReconnectConnectedAtMs = lastReconnectConnectedAtMs,
            lastReconnectLatencyMs = lastReconnectLatencyMs,
            lastDisconnectToReconnectLatencyMs = lastDisconnectToReconnectLatencyMs,
            lastReconnectToConnectedLatencyMs = lastReconnectToConnectedLatencyMs,
        )
    }
    fun getRegistrationRecord(
        packageName: String,
        androidUserId: Int,
    ): PushRegistrationRecord? = state.withLock {
        val userId = Utils.requireValidUserId(androidUserId)
        registrationRecords[RuntimeDeterministicCoordinator.packageScope(packageName, userId)]
    }

    fun getRegistrationRecords(): List<PushRegistrationRecord> = state.withLock {
        registrationRecords.values.toList()
    }

    fun getChannelRecords(): List<PushChannelRecord> = state.withLock {
        channelRecords.values.toList()
    }

    fun capabilities(): PushRuntimeCapabilities = PushRuntimeCapabilities(
        runtimeApiVersion = PUSH_RUNTIME_API_VERSION,
        capabilities = listOf(
            PushRuntimeCapability.BRIDGE_RUNTIME_SPINE,
            PushRuntimeCapability.LEGACY_MAIN_SERVICE_COMPONENT,
            PushRuntimeCapability.REGISTRATION_RUNTIME,
            PushRuntimeCapability.DOWNSTREAM_MESSAGE_PIPELINE,
            PushRuntimeCapability.NOTIFICATION_POLICY_RUNTIME,
            PushRuntimeCapability.CHANNEL_LIFECYCLE_TRACKING,
            PushRuntimeCapability.CONNECTION_SESSION_RUNTIME,
            PushRuntimeCapability.STOCK_SURFACE_COMPATIBILITY,
        )
    )
}
