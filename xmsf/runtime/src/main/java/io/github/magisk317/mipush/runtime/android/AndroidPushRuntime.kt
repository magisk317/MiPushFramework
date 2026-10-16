package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE

import android.content.Intent
import android.os.SystemClock
import io.github.magisk317.mipush.runtime.core.PushConnectionRecord
import io.github.magisk317.mipush.runtime.core.PushConnectionState
import io.github.magisk317.mipush.runtime.core.PushRuntimeApplicationDispatchResult
import io.github.magisk317.mipush.runtime.core.PushRuntimeExecutionHost
import io.github.magisk317.mipush.runtime.core.PushRuntimeRegistrationDispatchResult

/**
 * Process-wide facade over the push-runtime coordinators.
 *
 * Registration records live in AndroidPushRuntimeRegistration.kt and the
 * observation counters in AndroidPushRuntimeObservation.kt, both as same-package
 * extension seams, so call sites keep the `AndroidPushRuntime.foo(...)` syntax.
 */
object AndroidPushRuntime {
    internal val state = AndroidPushRuntimeState()
    private val bridgeIntentDispatcher = RuntimeBridgeIntentDispatcher(state)
    internal val connectionObservationCoordinator = RuntimeConnectionObservationCoordinator(state)
    internal val snapshotProjectionCoordinator = RuntimeSnapshotProjectionCoordinator(state)
    internal val registrationCoordinator = RuntimeRegistrationCoordinator(
        state = state,
        packageScope = { packageName, androidUserId ->
            RuntimeDeterministicCoordinator.packageScope(packageName, androidUserId)
        },
        buildReason = { source, reason -> RuntimeDeterministicCoordinator.buildReason(source, reason) },
        replayWindowMs = AndroidPushRuntimeWindowSupport.REGISTRATION_REPLAY_WINDOW_MS,
        pruneWindows = { nowMs -> AndroidPushRuntimeWindowSupport.pruneMessageWindows(state, nowMs) },
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
    fun requestFrameworkRegistration(source: String, reason: String? = null): Boolean =
        requestFrameworkRegistrationForUser(
            source = source,
            reason = reason,
            androidUserId = RuntimeDeterministicCoordinator.currentUserId(),
        )

    @JvmStatic
    fun requestApplicationRegistration(
        packageName: String,
        source: String,
        reason: String? = null,
        androidUserId: Int,
    ): Boolean {
        observeRegistrationRequest(
            packageName = packageName,
            source = source,
            reason = reason,
            androidUserId = androidUserId,
        )
        return replayApplicationRegistration(packageName, source, reason, androidUserId)
    }

    @JvmStatic
    fun handleBootCompleted(source: String): PushRuntimeRegistrationDispatchResult {
        val androidUserId = RuntimeDeterministicCoordinator.currentUserId()
        synchronizePersistedRegistrationState(androidUserId)
        val frameworkTriggered = requestFrameworkRegistrationForUser(source, "boot_completed", androidUserId)
        val connectionTriggered = requestConnection(source, "boot_completed")
        val replayed = replayPendingApplicationRegistrations(
            source,
            reason = "boot_completed",
            androidUserId = androidUserId,
        )
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
    fun handleNetworkAvailable(source: String): PushRuntimeRegistrationDispatchResult =
        handleNetworkAvailableForUser(source, RuntimeDeterministicCoordinator.currentUserId())

    /** Explicit-user seam used by cross-user callers and JVM contract tests. */
    internal fun handleNetworkAvailableForUser(
        source: String,
        androidUserId: Int,
        nowElapsedMs: Long = currentElapsedRealtime(),
    ): PushRuntimeRegistrationDispatchResult {
        Utils.requireValidUserId(androidUserId)
        val host = state.withLock { executionHost }
        val processTriggered = if (host == null) {
            false
        } else if (state.withLock {
                networkRegistrationThrottle.shouldThrottle(androidUserId, nowElapsedMs)
            }) {
            logD("skip pending registration processing during network burst userId=$androidUserId source=$source")
            false
        } else {
            runCatching { host.processPendingRegisterTasks(RuntimeDeterministicCoordinator.buildReason(source, "network_available")) }
                .getOrElse {
                    logE("processPendingRegisterTasks failed", it)
                    false
                }
        }
        val frameworkTriggered = requestFrameworkRegistrationForUser(source, "network_available", androidUserId)
        val connectionTriggered = requestConnection(source, "network_available")
        val replayed = replayPendingApplicationRegistrations(
            source,
            reason = "network_available",
            androidUserId = androidUserId,
        )
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
        launchApp: Boolean,
        notified: Boolean = false,
        androidUserId: Int,
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
                launchApp = launchApp,
                notified = notified
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
                source = source,
                androidUserId = androidUserId,
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
    fun capabilities(): PushRuntimeCapabilities = snapshotProjectionCoordinator.capabilities()

    @JvmStatic
    fun clearPackageTransientState(packageName: String) {
        clearPackageTransientState(packageName, RuntimeDeterministicCoordinator.currentUserId())
    }

    @JvmStatic
    fun clearPackageTransientState(packageName: String, userId: Int) {
        val normalizedUserId = Utils.requireValidUserId(userId)
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
            networkRegistrationThrottle.reset()
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

    private fun currentElapsedRealtime(): Long =
        runCatching { SystemClock.elapsedRealtime() }
            .getOrElse { System.currentTimeMillis() }

}
