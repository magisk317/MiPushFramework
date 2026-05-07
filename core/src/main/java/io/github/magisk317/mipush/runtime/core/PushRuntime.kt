package io.github.magisk317.mipush.runtime.core

import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier

data class PushRuntimeSnapshot(
    val bridgeReady: Boolean,
    val executionReady: Boolean,
    val pendingBridgeIntentCount: Int,
    val connectionState: PushConnectionState,
    val trackedChannelCount: Int,
    val boundChannelCount: Int,
    val trackedRegistrationCount: Int,
    val registeredPackageCount: Int,
    val downstreamMessageCount: Long,
    val deliveredToAppCount: Long,
    val duplicateMessageCount: Long,
    val ackMessageCount: Long,
    val broadcastFallbackDeliveryCount: Long,
    val notificationCancelCount: Long,
    val notificationEventCount: Long,
    val channelEventCount: Long,
    val accountEventCount: Long,
    val lastPackageName: String?,
    val lastAction: String?,
    val lastChannelPackage: String?,
    val lastChannelState: PushChannelState?,
    val lastRegistrationPackage: String?,
    val lastRegistrationState: PushRegistrationState?
)

data class PushRuntimeCapabilities(
    val runtimeApiVersion: Int,
    val capabilities: List<String>
)

interface PushRuntimeBridgeHost {
    val context: Context

    fun onRuntimeStarted() {}

    fun onRuntimeStopped() {}

    fun processBridgeIntent(intent: Intent)
}

object PushRuntime {
    private const val MAX_PENDING_BRIDGE_INTENTS = 32
    private const val MESSAGE_DEDUP_WINDOW_MS = 60_000L
    private const val APP_ACTION_BURST_WINDOW_MS = 2_000L
    private const val REGISTRATION_REPLAY_WINDOW_MS = 30_000L
    private const val MAX_REGISTRATION_RECORDS = 512
    private const val MAX_CHANNEL_RECORDS = 256
    private val logger = object {
        fun d(message: String) = Napier.d(message, tag = "PushRuntime")
        fun e(message: String, throwable: Throwable) = Napier.e(message, throwable, tag = "PushRuntime")
    }

    private val lock = Any()
    private val pendingBridgeIntents = PendingRuntimeQueue<Intent>(MAX_PENDING_BRIDGE_INTENTS)
    private val recentMessageIds = LinkedHashMap<String, Long>()
    private val recentPackageActions = LinkedHashMap<String, Long>()
    private val recentRegistrationReplays = LinkedHashMap<String, Long>()
    private val activeRegistrationDispatches = mutableSetOf<String>()
    private val registrationRecords = LinkedHashMap<String, PushRegistrationRecord>()
    private val channelRecords = LinkedHashMap<String, PushChannelRecord>()
    private var bridgeHost: PushRuntimeBridgeHost? = null
    private var executionHost: PushRuntimeExecutionHost? = null
    private var connectionRecord = PushConnectionRecord(
        state = PushConnectionState.Idle,
        updatedAtMs = 0L,
        source = "initial"
    )
    private var downstreamMessageCount: Long = 0
    private var deliveredToAppCount: Long = 0
    private var duplicateMessageCount: Long = 0
    private var ackMessageCount: Long = 0
    private var broadcastFallbackDeliveryCount: Long = 0
    private var notificationCancelCount: Long = 0
    private var notificationEventCount: Long = 0
    private var channelEventCount: Long = 0
    private var accountEventCount: Long = 0
    private var lastPackageName: String? = null
    private var lastAction: String? = null
    private var lastChannelPackage: String? = null
    private var lastChannelState: PushChannelState? = null
    private var lastRegistrationPackage: String? = null
    private var lastRegistrationState: PushRegistrationState? = null

    @JvmStatic
    fun attachBridgeHost(host: PushRuntimeBridgeHost) {
        val pending = synchronized(lock) {
            bridgeHost = host
            pendingBridgeIntents.drain()
        }
        runCatching { host.onRuntimeStarted() }
            .onFailure { logger.e("bridge host start failed", it) }
        pending.forEach { dispatchToHost(host, it) }
    }

    @JvmStatic
    fun attachExecutionHost(host: PushRuntimeExecutionHost) {
        synchronized(lock) {
            executionHost = host
        }
        logger.d("execution host attached")
    }

    @JvmStatic
    fun detachExecutionHost(host: PushRuntimeExecutionHost) {
        synchronized(lock) {
            if (executionHost === host) {
                executionHost = null
            }
        }
        logger.d("execution host detached")
    }

    @JvmStatic
    fun detachBridgeHost(host: PushRuntimeBridgeHost) {
        val shouldStop = synchronized(lock) {
            if (bridgeHost !== host) return
            bridgeHost = null
            true
        }
        if (shouldStop) {
            runCatching { host.onRuntimeStopped() }
                .onFailure { logger.e("bridge host stop failed", it) }
        }
    }

    @JvmStatic
    fun submitBridgeIntent(intent: Intent) {
        val host = synchronized(lock) {
            val activeHost = bridgeHost
            if (activeHost == null) {
                pendingBridgeIntents.offer(Intent(intent))
            }
            activeHost
        }
        if (host != null) {
            dispatchToHost(host, Intent(intent))
        }
    }

    @JvmStatic
    fun snapshot(): PushRuntimeSnapshot = synchronized(lock) {
        PushRuntimeSnapshot(
            bridgeReady = bridgeHost != null,
            executionReady = executionHost != null,
            pendingBridgeIntentCount = pendingBridgeIntents.size(),
            connectionState = connectionRecord.state,
            trackedChannelCount = channelRecords.size,
            boundChannelCount = channelRecords.values.count { it.state == PushChannelState.Bound },
            trackedRegistrationCount = registrationRecords.size,
            registeredPackageCount = registrationRecords.values.count { it.state == PushRegistrationState.Registered },
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
            lastRegistrationState = lastRegistrationState
        )
    }

    @JvmStatic
    fun requestFrameworkRegistration(source: String, reason: String? = null): Boolean {
        observeRegistrationRequest(
            packageName = PushRuntimeComponents.SERVICE_PACKAGE,
            source = source,
            reason = reason
        )
        val host = synchronized(lock) { executionHost } ?: return false
        return runCatching {
            host.requestFrameworkRegistration(reason = buildReason(source, reason))
        }.getOrElse {
            logger.e("framework registration dispatch failed", it)
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
        return PushRuntimeRegistrationDispatchResult(
            frameworkRegistrationTriggered = frameworkTriggered,
            pendingAppReplayCount = replayed,
            connectionEnsureTriggered = connectionTriggered
        )
    }

    @JvmStatic
    fun handleNetworkAvailable(source: String): PushRuntimeRegistrationDispatchResult {
        val host = synchronized(lock) { executionHost }
        val processTriggered = if (host == null) {
            false
        } else {
            runCatching { host.processPendingRegisterTasks(buildReason(source, "network_available")) }
                .getOrElse {
                    logger.e("processPendingRegisterTasks failed", it)
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
    fun handleAccountChanged(source: String): PushRuntimeRegistrationDispatchResult {
        val host = synchronized(lock) { executionHost }
        val accountTriggered = if (host == null) {
            false
        } else {
            runCatching { host.syncAccountAlias(buildReason(source, "account_changed")) }
                .getOrElse {
                    logger.e("syncAccountAlias failed", it)
                    false
                }
        }
        val replayed = replayPendingApplicationRegistrations(source, reason = "account_changed")
        return PushRuntimeRegistrationDispatchResult(
            pendingAppReplayCount = replayed,
            accountSyncTriggered = accountTriggered
        )
    }

    @JvmStatic
    fun requestConnection(source: String, reason: String? = null): Boolean {
        val host = synchronized(lock) { executionHost } ?: return false
        return runCatching {
            host.ensureConnection(reason = buildReason(source, reason))
        }.getOrElse {
            logger.e("ensureConnection failed", it)
            false
        }
    }

    @JvmStatic
    fun requestConnectionReset(source: String, reason: String? = null): Boolean {
        val host = synchronized(lock) { executionHost } ?: return false
        return runCatching {
            host.resetConnection(reason = buildReason(source, reason))
        }.getOrElse {
            logger.e("resetConnection failed", it)
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
        val host = synchronized(lock) { executionHost } ?: return PushRuntimeApplicationDispatchResult()
        val result = runCatching {
            host.dispatchDownstreamPayload(
                payload = payload,
                source = buildReason(source, if (launchApp) "launch_app" else "direct_deliver"),
                launchApp = launchApp
            )
        }.getOrElse {
            logger.e("dispatchDownstreamPayload failed", it)
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
            synchronized(lock) {
                broadcastFallbackDeliveryCount += 1
            }
        }
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
        val host = synchronized(lock) { executionHost } ?: return false
        val cancelled = runCatching {
            host.cancelNotificationForPayload(
                payload = payload,
                notificationId = notificationId,
                notificationGroup = notificationGroup,
                source = source
            )
        }.getOrElse {
            logger.e("cancelNotificationForPayload failed", it)
            false
        }
        if (cancelled) {
            synchronized(lock) {
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
    fun getRegistrationRecord(packageName: String): PushRegistrationRecord? = synchronized(lock) {
        registrationRecords[packageName]
    }

    @JvmStatic
    fun getRegistrationRecords(): List<PushRegistrationRecord> = synchronized(lock) {
        registrationRecords.values.toList()
    }

    @JvmStatic
    fun observeInboundMessage(
        packageName: String?,
        action: String,
        messageId: String?,
        source: String,
        isAck: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        synchronized(lock) {
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
            logger.d(
                "observeInboundMessage pkg=$packageName action=$action source=$source " +
                    "messageId=$messageId duplicated=$duplicated ack=$isAck"
            )
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
        synchronized(lock) {
            deliveredToAppCount += 1
            updateLastObservationLocked(packageName, action)
            markMessageIdentityLocked(packageName, action, messageId, nowMs)
        }
        logger.d("observeTransferToApplication pkg=$packageName action=$action source=$source")
    }

    @JvmStatic
    fun observeNotificationEvent(
        packageName: String?,
        action: String,
        source: String
    ) {
        synchronized(lock) {
            notificationEventCount += 1
            updateLastObservationLocked(packageName, action)
        }
        logger.d("observeNotificationEvent pkg=$packageName action=$action source=$source")
    }

    @JvmStatic
    fun observeChannelEvent(
        packageName: String?,
        action: String,
        source: String
    ) {
        synchronized(lock) {
            channelEventCount += 1
            updateLastObservationLocked(packageName, action)
        }
        logger.d("observeChannelEvent pkg=$packageName action=$action source=$source")
    }

    @JvmStatic
    @JvmOverloads
    fun observeConnectionState(
        state: PushConnectionState,
        source: String,
        host: String? = null,
        reason: String? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PushConnectionRecord {
        val record = PushConnectionRecord(
            state = state,
            updatedAtMs = nowMs,
            source = source,
            host = host,
            reason = reason
        )
        synchronized(lock) {
            connectionRecord = record
            updateLastObservationLocked(lastPackageName, "connection:${state.name}")
        }
        logger.d("observeConnectionState state=$state source=$source host=$host reason=$reason")
        return record
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
        val record = PushChannelRecord(
            packageName = packageName,
            channelId = channelId,
            userId = userId,
            session = session,
            state = state,
            updatedAtMs = nowMs,
            source = source,
            reasonCode = reasonCode,
            reasonMessage = reasonMessage
        )
        synchronized(lock) {
            channelRecords[channelIdentity(record)] = record
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
        val incomingKeys = channels.mapTo(linkedSetOf()) { channelIdentity(it) }
        synchronized(lock) {
            connectionRecord = PushConnectionRecord(
                state = connectionState,
                updatedAtMs = nowMs,
                source = source,
                host = host
            )
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
            channels.forEach { channelRecords[channelIdentity(it)] = it.copy(updatedAtMs = nowMs, source = source) }
        }
        observeChannelEvent(null, "channel_sync", source)
    }

    @JvmStatic
    fun getChannelRecords(): List<PushChannelRecord> = synchronized(lock) {
        channelRecords.values.toList()
    }

    @JvmStatic
    fun observeAccountEvent(
        action: String,
        source: String
    ) {
        synchronized(lock) {
            accountEventCount += 1
            updateLastObservationLocked(lastPackageName, action)
        }
        logger.d("observeAccountEvent action=$action source=$source")
    }

    @JvmStatic
    fun capabilities(): PushRuntimeCapabilities {
        return PushRuntimeCapabilities(
            runtimeApiVersion = BuildConfig.RUNTIME_API_VERSION,
            capabilities = listOf(
                PushRuntimeCapability.BRIDGE_RUNTIME_SPINE,
                PushRuntimeCapability.LEGACY_MAIN_SERVICE_COMPONENT,
                PushRuntimeCapability.REGISTRATION_RUNTIME,
                PushRuntimeCapability.DOWNSTREAM_MESSAGE_PIPELINE,
                PushRuntimeCapability.NOTIFICATION_POLICY_RUNTIME,
                PushRuntimeCapability.CHANNEL_LIFECYCLE_TRACKING,
                PushRuntimeCapability.CONNECTION_SESSION_RUNTIME,
                PushRuntimeCapability.STOCK_SURFACE_COMPATIBILITY,
                PushRuntimeCapability.ACCOUNT_CLOUD_BRIDGE,
            )
        )
    }

    @JvmStatic
    fun forceTriggerRegistration(packageName: String, source: String, reason: String? = null): Boolean {
        synchronized(lock) {
            if (activeRegistrationDispatches.contains(packageName)) {
                logger.d("skip reentrant application registration package=$packageName source=$source reason=$reason")
                return false
            }
            recentRegistrationReplays.remove(packageName)
            recentPackageActions.remove("$packageName:registration:Registering")
        }
        return requestApplicationRegistration(packageName, source, reason)
    }

    @JvmStatic
    fun clearStateForTests() {
        synchronized(lock) {
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

    private fun dispatchToHost(host: PushRuntimeBridgeHost, intent: Intent) {
        runCatching {
            host.processBridgeIntent(intent)
        }.onFailure {
            logger.e("bridge intent processing failed: action=${intent.action}", it)
        }
    }

    private fun updateRegistrationRecord(
        packageName: String,
        state: PushRegistrationState,
        source: String,
        reason: String?,
        nowMs: Long
    ): PushRegistrationRecord {
        val record = PushRegistrationRecord(
            packageName = packageName,
            state = state,
            updatedAtMs = nowMs,
            source = source,
            reason = reason
        )
        synchronized(lock) {
            registrationRecords[packageName] = record
            evictOldestIfNeeded(registrationRecords, MAX_REGISTRATION_RECORDS)
            lastRegistrationPackage = packageName
            lastRegistrationState = state
            updateLastObservationLocked(packageName, "registration:${state.name}")
        }
        logger.d("updateRegistrationRecord pkg=$packageName state=$state source=$source reason=$reason")
        return record
    }

    private fun updateLastObservationLocked(packageName: String?, action: String) {
        lastPackageName = packageName
        lastAction = action
    }

    private fun pruneMessageWindowsLocked(nowMs: Long) {
        pruneWindowLocked(recentMessageIds, nowMs, MESSAGE_DEDUP_WINDOW_MS)
        pruneWindowLocked(recentPackageActions, nowMs, APP_ACTION_BURST_WINDOW_MS)
        pruneWindowLocked(recentRegistrationReplays, nowMs, REGISTRATION_REPLAY_WINDOW_MS)
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
            val previous = recentMessageIds[messageId]
            duplicated = previous != null && (nowMs - previous) <= MESSAGE_DEDUP_WINDOW_MS
            recentMessageIds[messageId] = nowMs
        }
        if (!duplicated && !packageName.isNullOrBlank()) {
            val appActionKey = "$packageName:$action"
            val previous = recentPackageActions[appActionKey]
            duplicated = previous != null && (nowMs - previous) <= APP_ACTION_BURST_WINDOW_MS
            recentPackageActions[appActionKey] = nowMs
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
            recentMessageIds[messageId] = nowMs
        }
        if (!packageName.isNullOrBlank()) {
            recentPackageActions["$packageName:$action"] = nowMs
        }
    }

    private fun replayPendingApplicationRegistrations(
        source: String,
        reason: String,
        limit: Int = 8
    ): Int {
        val pendingPackages: List<String>
        val nowMs = System.currentTimeMillis()
        synchronized(lock) {
            pruneMessageWindowsLocked(nowMs)
            if (executionHost == null) return 0
            pendingPackages = registrationRecords.values
                .asSequence()
                .filter { it.packageName != PushRuntimeComponents.SERVICE_PACKAGE }
                .filter { it.state == PushRegistrationState.Registering || it.state == PushRegistrationState.Failed || it.state == PushRegistrationState.NotRegistered }
                .filterNot { activeRegistrationDispatches.contains(it.packageName) }
                .filter { shouldReplayRegistrationLocked(it.packageName, nowMs) }
                .take(limit)
                .map { it.packageName }
                .toList()
        }
        var dispatched = 0
        pendingPackages.forEach { packageName ->
            if (dispatchApplicationRegistration(packageName, source, reason)) {
                dispatched += 1
            }
        }
        if (dispatched > 0) {
            logger.d("replayed pending application registrations count=$dispatched source=$source reason=$reason")
        }
        return dispatched
    }

    private fun replayApplicationRegistration(packageName: String, source: String, reason: String?): Boolean {
        return dispatchApplicationRegistration(packageName, source, reason)
    }

    private fun dispatchApplicationRegistration(packageName: String, source: String, reason: String?): Boolean {
        val host = synchronized(lock) {
            if (activeRegistrationDispatches.contains(packageName)) {
                logger.d("skip active application registration package=$packageName source=$source reason=$reason")
                return false
            }
            val activeHost = executionHost ?: return false
            recentRegistrationReplays[packageName] = System.currentTimeMillis()
            activeRegistrationDispatches += packageName
            activeHost
        }
        return runCatching {
            host.requestApplicationRegistration(packageName, buildReason(source, reason))
        }.getOrElse {
            logger.e("requestApplicationRegistration failed package=$packageName", it)
            false
        }.also {
            synchronized(lock) {
                activeRegistrationDispatches -= packageName
            }
        }
    }

    private fun shouldReplayRegistrationLocked(packageName: String, nowMs: Long): Boolean {
        val previous = recentRegistrationReplays[packageName] ?: return true
        return (nowMs - previous) > REGISTRATION_REPLAY_WINDOW_MS
    }

    private fun buildReason(source: String, reason: String?): String {
        return if (reason.isNullOrBlank()) source else "$source:$reason"
    }

    private fun channelIdentity(record: PushChannelRecord): String {
        return buildString {
            append(record.channelId)
            append(':')
            append(record.packageName ?: "")
            append(':')
            append(record.userId ?: "")
            append(':')
            append(record.session ?: "")
        }
    }

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
