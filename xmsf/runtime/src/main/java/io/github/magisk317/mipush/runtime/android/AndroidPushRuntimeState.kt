package io.github.magisk317.mipush.runtime.android

import android.content.Intent
import io.github.magisk317.mipush.runtime.core.PushChannelRecord
import io.github.magisk317.mipush.runtime.core.PushChannelState
import io.github.magisk317.mipush.runtime.core.PushConnectionRecord
import io.github.magisk317.mipush.runtime.core.PushConnectionState
import io.github.magisk317.mipush.runtime.core.PushRegistrationRecord
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import io.github.magisk317.mipush.runtime.core.PushRuntimeExecutionHost
import io.github.magisk317.mipush.runtime.core.NetworkRegistrationThrottlePolicy

/**
 * The sole synchronization and mutable-record owner for [AndroidPushRuntime].
 * All access must happen through [withLock], so runtime snapshots observe one coherent state.
 */
internal class AndroidPushRuntimeState {
    @PublishedApi
    internal val lock = Any()

    internal val pendingBridgeIntents = PendingRuntimeQueue<Intent>(MAX_PENDING_BRIDGE_INTENTS)
    internal val recentMessageIds = LinkedHashMap<String, Long>()
    internal val recentPackageActions = LinkedHashMap<String, Long>()
    internal val recentRegistrationReplays = LinkedHashMap<String, Long>()
    internal val activeRegistrationDispatches = mutableSetOf<String>()
    internal val networkRegistrationThrottle = NetworkRegistrationThrottlePolicy()
    internal val registrationRecords = LinkedHashMap<String, PushRegistrationRecord>()
    internal val channelRecords = LinkedHashMap<String, PushChannelRecord>()
    internal var bridgeHost: PushRuntimeBridgeHost? = null
    internal var executionHost: PushRuntimeExecutionHost? = null
    internal var connectionRecord = PushConnectionRecord(
        state = PushConnectionState.Idle,
        updatedAtMs = 0L,
        source = "initial",
    )
    internal var connectedAtMs = 0L
    internal var lastDisconnectedAtMs = 0L
    internal var connectionSessionCount = 0L
    internal var lastResolvedIp: String? = null
    internal var lastPingSentAtMs = 0L
    internal var lastReadAliveAtMs = 0L
    internal var lastPingTimeoutAtMs = 0L
    internal var lastDisconnectReason: Int? = null
    internal var lastReconnectStartedAtMs = 0L
    internal var lastReconnectConnectedAtMs = 0L
    internal var lastReconnectLatencyMs = 0L
    internal var lastDisconnectToReconnectLatencyMs = 0L
    internal var lastReconnectToConnectedLatencyMs = 0L
    internal var downstreamMessageCount = 0L
    internal var deliveredToAppCount = 0L
    internal var duplicateMessageCount = 0L
    internal var ackMessageCount = 0L
    internal var broadcastFallbackDeliveryCount = 0L
    internal var notificationCancelCount = 0L
    internal var notificationEventCount = 0L
    internal var channelEventCount = 0L
    internal var accountEventCount = 0L
    internal var lastPackageName: String? = null
    internal var lastAction: String? = null
    internal var lastChannelPackage: String? = null
    internal var lastChannelState: PushChannelState? = null
    internal var lastRegistrationPackage: String? = null
    internal var lastRegistrationState: PushRegistrationState? = null

    internal inline fun <T> withLock(block: AndroidPushRuntimeState.() -> T): T =
        synchronized(lock) { block() }

    private companion object {
        const val MAX_PENDING_BRIDGE_INTENTS = 32
    }
}
