package io.github.magisk317.mipush.runtime.android

import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.runtime.core.PushChannelRecord
import io.github.magisk317.mipush.runtime.core.PushChannelState
import io.github.magisk317.mipush.runtime.core.PushConnectionRecord
import io.github.magisk317.mipush.runtime.core.PushConnectionState

/**
 * Observation seams of [AndroidPushRuntime]: connection, channel, message and
 * account events plus the inbound dedupe window. Split out of the facade
 * object; the same-package extensions keep `AndroidPushRuntime.foo(...)` call
 * sites unchanged, and every counter still mutates under the single
 * [AndroidPushRuntimeState] lock.
 */

fun AndroidPushRuntime.observePingSent(atMs: Long) = connectionObservationCoordinator.observePingSent(atMs)

fun AndroidPushRuntime.observeReadAlive(atMs: Long) = connectionObservationCoordinator.observeReadAlive(atMs)

fun AndroidPushRuntime.observePingTimeout(atMs: Long) = connectionObservationCoordinator.observePingTimeout(atMs)

fun AndroidPushRuntime.observeReconnectStarted(atMs: Long) =
    connectionObservationCoordinator.observeReconnectStarted(atMs)

fun AndroidPushRuntime.observeDisconnectReason(reason: Int?) =
    connectionObservationCoordinator.observeDisconnectReason(reason)

fun AndroidPushRuntime.observeReconnectConnected(atMs: Long) =
    connectionObservationCoordinator.observeReconnectConnected(atMs)

fun AndroidPushRuntime.observeInboundMessage(
    packageName: String?,
    action: String,
    messageId: String?,
    source: String,
    isAck: Boolean = false,
    nowMs: Long = System.currentTimeMillis(),
    androidUserId: Int,
): Boolean {
    state.withLock {
        AndroidPushRuntimeWindowSupport.pruneMessageWindows(state, nowMs)
        val duplicated = AndroidPushRuntimeWindowSupport.isDuplicate(
            state, packageName, action, messageId, nowMs, androidUserId,
        )
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

fun AndroidPushRuntime.observeTransferToApplication(
    packageName: String?,
    action: String,
    messageId: String?,
    source: String,
    nowMs: Long = System.currentTimeMillis(),
    androidUserId: Int,
) {
    Utils.requireValidUserId(androidUserId)
    state.withLock {
        deliveredToAppCount += 1
        updateLastObservationLocked(packageName, action)
        AndroidPushRuntimeWindowSupport.markMessageIdentity(
            state, packageName, action, messageId, nowMs, androidUserId,
        )
    }
}

fun AndroidPushRuntime.observeNotificationEvent(
    packageName: String?,
    action: String,
    source: String
) {
    state.withLock {
        notificationEventCount += 1
        updateLastObservationLocked(packageName, action)
    }
}

fun AndroidPushRuntime.observeChannelEvent(
    packageName: String?,
    action: String,
    source: String
) {
    state.withLock {
        channelEventCount += 1
        updateLastObservationLocked(packageName, action)
    }
}

fun AndroidPushRuntime.observeConnectionState(
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
        connectionObservationCoordinator.applyConnectionState(
            record = record,
            nowMs = nowMs,
            resolvedIp = resolvedIp,
        )
        updateLastObservationLocked(lastPackageName, "connection:${state.name}")
    }
    return record
}

fun AndroidPushRuntime.observeChannelState(
    packageName: String?,
    channelId: String,
    userId: String?,
    session: String?,
    state: PushChannelState,
    source: String,
    reasonCode: Int? = null,
    reasonMessage: String? = null,
    nowMs: Long = System.currentTimeMillis(),
    androidUserId: Int,
): PushChannelRecord {
    Utils.requireValidUserId(androidUserId)
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
        AndroidPushRuntimeWindowSupport.evictOldestIfNeeded(
            channelRecords,
            AndroidPushRuntimeWindowSupport.MAX_CHANNEL_RECORDS,
        )
        lastChannelPackage = packageName
        lastChannelState = state
    }
    observeChannelEvent(packageName, "channel:${state.name}", source)
    return record
}

fun AndroidPushRuntime.synchronizeChannels(
    connectionState: PushConnectionState,
    host: String?,
    channels: List<PushChannelRecord>,
    source: String,
    nowMs: Long = System.currentTimeMillis(),
    androidUserId: Int,
) {
    Utils.requireValidUserId(androidUserId)
    val scopedChannels = channels.map { it.copy(androidUserId = androidUserId, updatedAtMs = nowMs, source = source) }
    state.withLock {
        connectionObservationCoordinator.applyConnectionState(
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

fun AndroidPushRuntime.getChannelRecords(): List<PushChannelRecord> = snapshotProjectionCoordinator.getChannelRecords()

internal fun AndroidPushRuntime.channelIdentity(record: PushChannelRecord, androidUserId: Int): String =
    RuntimeDeterministicCoordinator.channelIdentity(record, androidUserId)

fun AndroidPushRuntime.observeAccountEvent(
    action: String,
    source: String
) {
    state.withLock {
        accountEventCount += 1
        updateLastObservationLocked(lastPackageName, action)
    }
}

internal fun AndroidPushRuntime.updateLastObservationLocked(packageName: String?, action: String) {
    state.lastPackageName = packageName
    state.lastAction = action
}
