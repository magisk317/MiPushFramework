package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.runtime.core.PushChannelRecord
import io.github.magisk317.mipush.runtime.core.PushChannelState
import io.github.magisk317.mipush.runtime.core.PushConnectionRecord
import io.github.magisk317.mipush.runtime.core.PushConnectionState
import io.github.magisk317.mipush.runtime.core.PushRegistrationRecord
import io.github.magisk317.mipush.runtime.core.PushRegistrationState
import io.github.magisk317.mipush.runtime.core.PushRuntimeApplicationDispatchResult
import io.github.magisk317.mipush.runtime.core.PushRuntimeComponents
import io.github.magisk317.mipush.runtime.core.PushRuntimeExecutionHost
import io.github.magisk317.mipush.runtime.core.PushRuntimeRegistrationDispatchResult

object AndroidPushRuntime {
    private const val MESSAGE_DEDUP_WINDOW_MS = 60_000L
    private const val APP_ACTION_BURST_WINDOW_MS = 2_000L
    private const val REGISTRATION_REPLAY_WINDOW_MS = 30_000L
    private const val MAX_REGISTRATION_RECORDS = 512
    private const val MAX_CHANNEL_RECORDS = 256

    private val state = AndroidPushRuntimeState()
    private val bridgeIntentDispatcher = RuntimeBridgeIntentDispatcher(state)
    private val connectionObservationCoordinator = RuntimeConnectionObservationCoordinator(state)
    private val snapshotProjectionCoordinator = RuntimeSnapshotProjectionCoordinator(state)
    private val registrationCoordinator = RuntimeRegistrationCoordinator(
        state = state,
        packageScope = { packageName -> RuntimeDeterministicCoordinator.packageScope(packageName) },
        buildReason = { source, reason -> RuntimeDeterministicCoordinator.buildReason(source, reason) },
        replayWindowMs = REGISTRATION_REPLAY_WINDOW_MS,
        pruneWindows = ::pruneMessageWindowsLocked,
    )

    @JvmStatic
    fun attachBridgeHost(host: PushRuntimeBridgeHost) {
        bridgeIntentDispatcher.attach(host)
    }

    @JvmStatic
    fun attachExecutionHost(host: PushRuntimeExecutionHost) {
        state.withLock {
            executionHost = host
        }
        logD("execution host attached")
    }

    @JvmStatic
    fun detachExecutionHost(host: PushRuntimeExecutionHost) {
        state.withLock {
            if (executionHost === host) {
                executionHost = null
            }
        }
        logD("execution host detached")
    }

    @JvmStatic
    fun detachBridgeHost(host: PushRuntimeBridgeHost) {
        bridgeIntentDispatcher.detach(host)
    }

    @JvmStatic
    fun submitBridgeIntent(intent: Intent) {
        bridgeIntentDispatcher.submit(intent)
    }

    @JvmStatic
    fun snapshot(): PushRuntimeSnapshot = snapshotProjectionCoordinator.snapshot()

    data class ConnectionSnapshotData(
        val connectionState: String,
        val connectedAtMs: Long,
        val lastDisconnectedAtMs: Long,
        val connectionSessionCount: Long,
        val serverHost: String?,
        val resolvedIp: String?,
        val downstreamMessageCount: Long,
        val deliveredToAppCount: Long,
        val duplicateMessageCount: Long,
        val ackMessageCount: Long,
        val registeredPackageCount: Int,
        val trackedChannelCount: Int,
        val boundChannelCount: Int,
        val lastPingSentAtMs: Long,
        val lastReadAliveAtMs: Long,
        val lastPingTimeoutAtMs: Long,
        val lastDisconnectReason: Int?,
        val lastReconnectStartedAtMs: Long,
        val lastReconnectConnectedAtMs: Long,
        val lastReconnectLatencyMs: Long,
        val lastDisconnectToReconnectLatencyMs: Long,
        val lastReconnectToConnectedLatencyMs: Long,
    )

    @JvmStatic
    fun connectionSnapshot(): ConnectionSnapshotData = snapshotProjectionCoordinator.connectionSnapshot()

    @JvmStatic
    fun observePingSent(atMs: Long) = connectionObservationCoordinator.observePingSent(atMs)

    @JvmStatic
    fun observeReadAlive(atMs: Long) = connectionObservationCoordinator.observeReadAlive(atMs)

    @JvmStatic
    fun observePingTimeout(atMs: Long) = connectionObservationCoordinator.observePingTimeout(atMs)

    @JvmStatic
    fun observeReconnectStarted(atMs: Long) = connectionObservationCoordinator.observeReconnectStarted(atMs)

    @JvmStatic
    fun observeDisconnectReason(reason: Int?) = connectionObservationCoordinator.observeDisconnectReason(reason)

    @JvmStatic
    fun observeReconnectConnected(atMs: Long) = connectionObservationCoordinator.observeReconnectConnected(atMs)

    @JvmStatic
    fun requestFrameworkRegistration(source: String, reason: String? = null): Boolean {
        observeRegistrationRequest(
            packageName = PushRuntimeComponents.SERVICE_PACKAGE,
            source = source,
            reason = reason
        )
        val host = state.withLock { executionHost } ?: return false
        return runCatching {
            host.requestFrameworkRegistration(reason = RuntimeDeterministicCoordinator.buildReason(source, reason))
        }.getOrElse {
            logE("framework registration dispatch failed", it)
            false
        }
    }

    @JvmStatic
    fun requestApplicationRegistration(packageName: String, source: String, reason: String? = null): Boolean {
        observeRegistrationRequest(
            packageName = packageName,
            source = source,
            reason = reason
        )
        return replayApplicationRegistration(packageName, source, reason)
    }

    @JvmStatic
    fun handleBootCompleted(source: String): PushRuntimeRegistrationDispatchResult {
        val frameworkTriggered = requestFrameworkRegistration(source, "boot_completed")
        val connectionTriggered = requestConnection(source, "boot_completed")
        val replayed = replayPendingApplicationRegistrations(source, reason = "boot_completed")
        MagiskOtel.event(
            name = "push.boot",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "runtime_boot",
                "reason" to "boot_completed",
                "source" to source,
                "pending_count" to replayed.toString(),
            ),
            statusOk = true,
        )
        return PushRuntimeRegistrationDispatchResult(
            frameworkRegistrationTriggered = frameworkTriggered,
            pendingAppReplayCount = replayed,
            connectionEnsureTriggered = connectionTriggered
        )
    }

    @JvmStatic
    fun handleNetworkAvailable(source: String): PushRuntimeRegistrationDispatchResult {
        val host = state.withLock { executionHost }
        val processTriggered = if (host == null) {
            false
        } else {
            runCatching { host.processPendingRegisterTasks(RuntimeDeterministicCoordinator.buildReason(source, "network_available")) }
                .getOrElse {
                    logE("processPendingRegisterTasks failed", it)
                    false
                }
        }
        val frameworkTriggered = requestFrameworkRegistration(source, "network_available")
        val connectionTriggered = requestConnection(source, "network_available")
        val replayed = replayPendingApplicationRegistrations(source, reason = "network_available")
        return PushRuntimeRegistrationDispatchResult(
            frameworkRegistrationTriggered = frameworkTriggered,
            pendingAppReplayCount = replayed,
            processRegisterTaskTriggered = processTriggered,
            connectionEnsureTriggered = connectionTriggered
        )
    }

    @JvmStatic
    fun requestConnection(source: String, reason: String? = null): Boolean {
        val host = state.withLock { executionHost } ?: run {
            MagiskOtel.event(
                name = "push.lifecycle",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "runtime_connection",
                    "reason" to "no_host",
                    "source" to source,
                ),
                statusOk = true,
            )
            return false
        }
        val ok = runCatching {
            host.ensureConnection(reason = RuntimeDeterministicCoordinator.buildReason(source, reason))
        }.getOrElse {
            logE("ensureConnection failed", it)
            MagiskOtel.event(
                name = "push.lifecycle",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "runtime_connection",
                    "reason" to (reason ?: "ensure_failed"),
                    "source" to source,
                    "error_class" to it.javaClass.simpleName,
                ),
                statusOk = false,
            )
            return false
        }
        MagiskOtel.event(
            name = "push.lifecycle",
            attributes = mapOf(
                "result" to if (ok) "ok" else "skip",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "runtime_connection",
                "reason" to (reason ?: "ensure"),
                "source" to source,
            ),
            statusOk = true,
        )
        return ok
    }

    @JvmStatic
    fun requestConnectionReset(source: String, reason: String? = null): Boolean {
        val host = state.withLock { executionHost } ?: return false
        return runCatching {
            host.resetConnection(reason = RuntimeDeterministicCoordinator.buildReason(source, reason))
        }.getOrElse {
            logE("resetConnection failed", it)
            false
        }
    }

    @JvmStatic
    fun dispatchDownstreamPayload(
        packageName: String?,
        action: String,
        messageId: String?,
        payload: ByteArray,
        source: String,
        launchApp: Boolean
    ): PushRuntimeApplicationDispatchResult {
        val host = state.withLock { executionHost } ?: run {
            MagiskOtel.event(
                name = "push.dispatch",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "runtime_dispatch",
                    "reason" to "no_host",
                    "source" to source,
                    "payload_size" to payload.size.toString(),
                ) + (packageName?.let { mapOf("target_package" to it) } ?: emptyMap()),
                statusOk = true,
            )
            return PushRuntimeApplicationDispatchResult()
        }
        val result = runCatching {
            host.dispatchDownstreamPayload(
                payload = payload,
                source = RuntimeDeterministicCoordinator.buildReason(source, if (launchApp) "launch_app" else "direct_deliver"),
                launchApp = launchApp
            )
        }.getOrElse {
            logE("dispatchDownstreamPayload failed", it)
            MagiskOtel.event(
                name = "push.dispatch",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "runtime_dispatch",
                    "reason" to "dispatch_exception",
                    "source" to source,
                    "payload_size" to payload.size.toString(),
                    "error_class" to it.javaClass.simpleName,
                ) + (packageName?.let { mapOf("target_package" to it) } ?: emptyMap()),
                statusOk = false,
            )
            PushRuntimeApplicationDispatchResult()
        }
        if (result.dispatched) {
            observeTransferToApplication(
                packageName = packageName,
                action = action,
                messageId = messageId,
                source = source
            )
        }
        if (result.deliveredByBroadcastFallback) {
            state.withLock {
                broadcastFallbackDeliveryCount += 1
            }
        }
        MagiskOtel.event(
            name = "push.dispatch",
            attributes = mapOf(
                "result" to if (result.dispatched) "ok" else "skip",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "runtime_dispatch",
                "reason" to when {
                    result.deliveredByBroadcastFallback -> "broadcast_fallback"
                    result.dispatched -> if (launchApp) "launch_app" else "direct_deliver"
                    else -> "not_dispatched"
                },
                "source" to source,
                "payload_size" to payload.size.toString(),
            ) + (packageName?.let { mapOf("target_package" to it) } ?: emptyMap()),
            statusOk = true,
        )
        return result
    }

    @JvmStatic
    fun cancelNotificationForPayload(
        packageName: String?,
        payload: ByteArray,
        notificationId: Int,
        notificationGroup: String?,
        source: String
    ): Boolean {
        val host = state.withLock { executionHost } ?: return false
        val cancelled = runCatching {
            host.cancelNotificationForPayload(
                payload = payload,
                notificationId = notificationId,
                notificationGroup = notificationGroup,
                source = source
            )
        }.getOrElse {
            logE("cancelNotificationForPayload failed", it)
            false
        }
        if (cancelled) {
            state.withLock {
                notificationCancelCount += 1
            }
            observeNotificationEvent(
                packageName = packageName,
                action = "cancel_notification",
                source = source
            )
        }
        return cancelled
    }

    @JvmStatic
    @JvmOverloads
    fun observeRegistrationRequest(
        packageName: String,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord {
        return updateRegistrationRecord(
            packageName = packageName,
            state = PushRegistrationState.Registering,
            source = source,
            reason = reason,
            nowMs = nowMs
        )
    }

    @JvmStatic
    @JvmOverloads
    fun observeRegistrationResult(
        packageName: String,
        success: Boolean,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord {
        return updateRegistrationRecord(
            packageName = packageName,
            state = if (success) PushRegistrationState.Registered else PushRegistrationState.Failed,
            source = source,
            reason = reason,
            nowMs = nowMs
        )
    }

    @JvmStatic
    @JvmOverloads
    fun observeUnregistration(
        packageName: String,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord {
        return updateRegistrationRecord(
            packageName = packageName,
            state = PushRegistrationState.Unregistered,
            source = source,
            reason = reason,
            nowMs = nowMs
        )
    }

    @JvmStatic
    @JvmOverloads
    fun observeRegistrationState(
        packageName: String,
        state: PushRegistrationState,
        source: String,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushRegistrationRecord {
        return updateRegistrationRecord(
            packageName = packageName,
            state = state,
            source = source,
            reason = reason,
            nowMs = nowMs
        )
    }

    @JvmStatic
    fun getRegistrationRecord(packageName: String): PushRegistrationRecord? = snapshotProjectionCoordinator.getRegistrationRecord(packageName)

    @JvmStatic
    fun getRegistrationRecords(): List<PushRegistrationRecord> = snapshotProjectionCoordinator.getRegistrationRecords()

    @JvmStatic
    fun observeInboundMessage(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        isAck: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        state.withLock {
            pruneMessageWindowsLocked(nowMs)
            val duplicated = isDuplicateLocked(packageName, action, messageId, nowMs)
            if (duplicated) {
                duplicateMessageCount += 1
            } else {
                downstreamMessageCount += 1
            }
            if (isAck) {
                ackMessageCount += 1
            }
            updateLastObservationLocked(packageName, action)
            if (duplicated) {
                logD(
                    "drop duplicate inbound message pkg=$packageName action=$action source=$source " +
                        "messageId=$messageId ack=$isAck"
                )
            }
            return !duplicated
        }
    }

    @JvmStatic
    fun observeTransferToApplication(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        state.withLock {
            deliveredToAppCount += 1
            updateLastObservationLocked(packageName, action)
            markMessageIdentityLocked(packageName, action, messageId, nowMs)
        }
    }

    @JvmStatic
    fun observeNotificationEvent(
        packageName: String?,
        action: String,
        source: String
    ) {
        state.withLock {
            notificationEventCount += 1
            updateLastObservationLocked(packageName, action)
        }
    }

    @JvmStatic
    fun observeChannelEvent(
        packageName: String?,
        action: String,
        source: String
    ) {
        state.withLock {
            channelEventCount += 1
            updateLastObservationLocked(packageName, action)
        }
    }

    @JvmStatic
    @JvmOverloads
    fun observeConnectionState(
        state: PushConnectionState,
        source: String,
        host: String? = null,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis(),
        resolvedIp: String? = null
    ): PushConnectionRecord {
        val record = PushConnectionRecord(
            state = state,
            updatedAtMs = nowMs,
            source = source,
            host = host,
            reason = reason
        )
        this.state.withLock {
            applyConnectionStateLocked(
                record = record,
                nowMs = nowMs,
                resolvedIp = resolvedIp,
            )
            updateLastObservationLocked(lastPackageName, "connection:${state.name}")
        }
        return record
    }

    private fun applyConnectionStateLocked(
        record: PushConnectionRecord,
        nowMs: Long,
        resolvedIp: String? = null,
    ) {
        val previousState = state.connectionRecord.state
        state.connectionRecord = record
        when (record.state) {
            PushConnectionState.Connected -> {
                val reconnectElapsedMs = if (state.lastDisconnectedAtMs > 0L) {
                    (nowMs - state.lastDisconnectedAtMs).takeIf { it >= 0L }
                } else {
                    null
                }
                val mergesPreviousSession =
                    previousState != PushConnectionState.Connected &&
                        state.connectionSessionCount > 0L &&
                        state.connectedAtMs > 0L &&
                        reconnectElapsedMs != null &&
                        reconnectElapsedMs <
                        io.github.magisk317.mipush.runtime.core.PushReconnectPolicy
                            .CONNECTION_SESSION_MERGE_WINDOW_MS

                if (previousState != PushConnectionState.Connected && !mergesPreviousSession) {
                    state.connectedAtMs = nowMs
                    state.connectionSessionCount += 1
                }
                if (resolvedIp != null) {
                    state.lastResolvedIp = resolvedIp
                }
            }

            PushConnectionState.Disconnected -> {
                if (previousState != PushConnectionState.Disconnected) {
                    state.lastDisconnectedAtMs = nowMs
                }
            }

            else -> Unit
        }
    }

    @JvmStatic
    @JvmOverloads
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
    ): PushChannelRecord {
        val androidUserId = RuntimeDeterministicCoordinator.currentUserId()
        val record = PushChannelRecord(
            packageName = packageName,
            channelId = channelId,
            userId = userId,
            session = session,
            state = state,
            updatedAtMs = nowMs,
            source = source,
            reasonCode = reasonCode,
            reasonMessage = reasonMessage,
            androidUserId = androidUserId,
        )
        this.state.withLock {
            channelRecords[RuntimeDeterministicCoordinator.channelIdentity(record, androidUserId)] = record
            evictOldestIfNeeded(channelRecords, MAX_CHANNEL_RECORDS)
            lastChannelPackage = packageName
            lastChannelState = state
        }
        observeChannelEvent(packageName, "channel:${state.name}", source)
        return record
    }

    @JvmStatic
    fun synchronizeChannels(
        connectionState: PushConnectionState,
        host: String?,
        channels: List<PushChannelRecord>,
        source: String,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val androidUserId = RuntimeDeterministicCoordinator.currentUserId()
        val scopedChannels = channels.map { it.copy(androidUserId = androidUserId, updatedAtMs = nowMs, source = source) }
        state.withLock {
            applyConnectionStateLocked(
                record = PushConnectionRecord(
                    state = connectionState,
                    updatedAtMs = nowMs,
                    source = source,
                    host = host
                ),
                nowMs = nowMs,
            )
            val incomingKeys = scopedChannels.mapTo(linkedSetOf()) { RuntimeDeterministicCoordinator.channelIdentity(it, androidUserId) }
            val iterator = channelRecords.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if (!incomingKeys.contains(entry.key) &&
                    (entry.value.state == PushChannelState.Bound || entry.value.state == PushChannelState.Binding)
                ) {
                    entry.setValue(
                        entry.value.copy(
                            state = PushChannelState.Unbound,
                            updatedAtMs = nowMs,
                            source = "$source:sync_missing"
                        )
                    )
                }
            }
            scopedChannels.forEach { channelRecords[RuntimeDeterministicCoordinator.channelIdentity(it, androidUserId)] = it }
        }
        observeChannelEvent(null, "channel_sync", source)
    }

    @JvmStatic
    fun getChannelRecords(): List<PushChannelRecord> = snapshotProjectionCoordinator.getChannelRecords()

    internal fun channelIdentity(record: PushChannelRecord, androidUserId: Int): String =
        RuntimeDeterministicCoordinator.channelIdentity(record, androidUserId)

    @JvmStatic
    fun observeAccountEvent(
        action: String,
        source: String
    ) {
        state.withLock {
            accountEventCount += 1
            updateLastObservationLocked(lastPackageName, action)
        }
    }

    @JvmStatic
    fun capabilities(): PushRuntimeCapabilities = snapshotProjectionCoordinator.capabilities()

    @JvmStatic
    fun forceTriggerRegistration(packageName: String, source: String, reason: String? = null): Boolean {
        if (!registrationCoordinator.clearReplayDedupeForForce(packageName)) {
            logD("skip reentrant application registration package=$packageName source=$source reason=$reason")
            MagiskOtel.event(
                name = "push.register",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "runtime_force",
                    "reason" to "reentrant",
                    "target_package" to packageName,
                    "source" to source,
                ),
                statusOk = true,
            )
            return false
        }
        val triggered = requestApplicationRegistration(packageName, source, reason)
        MagiskOtel.event(
            name = "push.register",
            attributes = mapOf(
                "result" to if (triggered) "ok" else "skip",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "runtime_force",
                "reason" to (reason ?: "force"),
                "target_package" to packageName,
                "source" to source,
            ),
            statusOk = true,
        )
        return triggered
    }

    @JvmStatic
    fun clearPackageTransientState(packageName: String) {
        clearPackageTransientState(packageName, RuntimeDeterministicCoordinator.currentUserId())
    }

    @JvmStatic
    fun clearPackageTransientState(packageName: String, userId: Int) {
        val normalizedUserId = userId.coerceAtLeast(0)
        val packageKey = "$normalizedUserId:$packageName"
        state.withLock {
            val packagePrefix = "$packageKey:"
            recentMessageIds.keys.removeIf { it.startsWith(packagePrefix) }
            recentPackageActions.keys.removeIf { it.startsWith(packagePrefix) }
            recentRegistrationReplays.remove(packageKey)
            activeRegistrationDispatches.remove(packageKey)
            registrationRecords.remove(packageKey)
            val userPrefix = "$normalizedUserId:"
            channelRecords.entries.removeIf { (key, record) ->
                key.startsWith(userPrefix) && record.packageName == packageName
            }
        }
    }

    @JvmStatic
    fun clearStateForTests() {
        state.withLock {
            pendingBridgeIntents.drain()
            recentMessageIds.clear()
            recentPackageActions.clear()
            recentRegistrationReplays.clear()
            activeRegistrationDispatches.clear()
            registrationRecords.clear()
            channelRecords.clear()
            bridgeHost = null
            executionHost = null
            connectionRecord = PushConnectionRecord(
                state = PushConnectionState.Idle,
                updatedAtMs = 0L,
                source = "test_reset"
            )
            connectedAtMs = 0L
            lastDisconnectedAtMs = 0L
            connectionSessionCount = 0L
            lastResolvedIp = null
            lastPingSentAtMs = 0L
            lastReadAliveAtMs = 0L
            lastPingTimeoutAtMs = 0L
            lastDisconnectReason = null
            lastReconnectStartedAtMs = 0L
            lastReconnectConnectedAtMs = 0L
            lastReconnectLatencyMs = 0L
            lastDisconnectToReconnectLatencyMs = 0L
            lastReconnectToConnectedLatencyMs = 0L
            downstreamMessageCount = 0
            deliveredToAppCount = 0
            duplicateMessageCount = 0
            ackMessageCount = 0
            broadcastFallbackDeliveryCount = 0
            notificationCancelCount = 0
            notificationEventCount = 0
            channelEventCount = 0
            accountEventCount = 0
            lastPackageName = null
            lastAction = null
            lastChannelPackage = null
            lastChannelState = null
            lastRegistrationPackage = null
            lastRegistrationState = null
        }
    }

    private fun updateRegistrationRecord(
        packageName: String,
        state: PushRegistrationState,
        source: String,
        reason: String?,
        nowMs: Long
    ): PushRegistrationRecord {
        val androidUserId = RuntimeDeterministicCoordinator.currentUserId()
        val record = PushRegistrationRecord(
            packageName = packageName,
            state = state,
            updatedAtMs = nowMs,
            source = source,
            reason = reason,
            androidUserId = androidUserId,
        )
        this.state.withLock {
            registrationRecords[RuntimeDeterministicCoordinator.packageScope(packageName, androidUserId)] = record
            evictOldestIfNeeded(registrationRecords, MAX_REGISTRATION_RECORDS)
            lastRegistrationPackage = packageName
            lastRegistrationState = state
            updateLastObservationLocked(packageName, "registration:${state.name}")
        }
        return record
    }

    private fun updateLastObservationLocked(packageName: String?, action: String) {
        state.lastPackageName = packageName
        state.lastAction = action
    }

    private fun pruneMessageWindowsLocked(nowMs: Long) {
        pruneWindowLocked(state.recentMessageIds, nowMs, MESSAGE_DEDUP_WINDOW_MS)
        pruneWindowLocked(state.recentPackageActions, nowMs, APP_ACTION_BURST_WINDOW_MS)
        pruneWindowLocked(state.recentRegistrationReplays, nowMs, REGISTRATION_REPLAY_WINDOW_MS)
    }

    private fun pruneWindowLocked(window: LinkedHashMap<String, Long>, nowMs: Long, ttlMs: Long) {
        val iterator = window.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((nowMs - entry.value) > ttlMs) {
                iterator.remove()
            }
        }
    }

    private fun isDuplicateLocked(
        packageName: String?,
        action: String,
        messageId: String?,
        nowMs: Long
    ): Boolean {
        var duplicated = false
        if (!messageId.isNullOrBlank()) {
            val messageKey = RuntimeDeterministicCoordinator.messageScope(packageName, messageId)
            val previous = state.recentMessageIds[messageKey]
            duplicated = previous != null && (nowMs - previous) <= MESSAGE_DEDUP_WINDOW_MS
            state.recentMessageIds[messageKey] = nowMs
        }
        if (!duplicated && !packageName.isNullOrBlank()) {
            val appActionKey = RuntimeDeterministicCoordinator.actionScope(packageName, action)
            val previous = state.recentPackageActions[appActionKey]
            duplicated = previous != null && (nowMs - previous) <= APP_ACTION_BURST_WINDOW_MS
            state.recentPackageActions[appActionKey] = nowMs
        }
        return duplicated
    }
    private fun markMessageIdentityLocked(
        packageName: String?,
        action: String,
        messageId: String?,
        nowMs: Long
    ) {
        if (!messageId.isNullOrBlank()) {
            state.recentMessageIds[RuntimeDeterministicCoordinator.messageScope(packageName, messageId)] = nowMs
        }
        if (!packageName.isNullOrBlank()) {
            state.recentPackageActions[RuntimeDeterministicCoordinator.actionScope(packageName, action)] = nowMs
        }
    }

    private fun replayPendingApplicationRegistrations(
        source: String,
        reason: String,
        limit: Int = 8,
    ): Int = registrationCoordinator.replayPending(source, reason, limit)

    private fun replayApplicationRegistration(packageName: String, source: String, reason: String?): Boolean =
        registrationCoordinator.dispatchApplication(packageName, source, reason)

    private fun dispatchApplicationRegistration(packageName: String, source: String, reason: String?): Boolean =
        registrationCoordinator.dispatchApplication(packageName, source, reason)

    /**
     * Evict oldest entries from a LinkedHashMap when it exceeds [maxSize].
     * Must be called under [lock].
     */
    private fun <K, V> evictOldestIfNeeded(map: LinkedHashMap<K, V>, maxSize: Int) {
        while (map.size > maxSize) {
            val firstKey = map.keys.firstOrNull() ?: break
            map.remove(firstKey)
        }
    }
}
