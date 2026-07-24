package io.github.magisk317.mipush.manager.connection

import io.github.magisk317.mipush.common.manager.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.SettingsManager
import io.github.magisk317.mipush.manager.api.ManagerConnectionSnapshotDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import kotlinx.coroutines.CancellationException

fun interface ConnectionSnapshotSource {
    suspend fun load(): ConnectionSnapshotSourceResult
}

sealed interface ConnectionSnapshotSourceResult {
    data class Available(
        val snapshot: ManagerConnectionSnapshot,
    ) : ConnectionSnapshotSourceResult

    data class Unavailable(
        val status: ConnectionSnapshotSourceStatus,
    ) : ConnectionSnapshotSourceResult
}

enum class ConnectionSnapshotSourceStatus {
    UNSUPPORTED,
    DISCONNECTED,
    BINDING,
    RUNTIME_MISSING,
    PERMISSION_DENIED,
    TIMED_OUT,
    INCOMPATIBLE,
    TEMPORARILY_DISCONNECTED,
    FAILED,
}

class InProcessConnectionSnapshotSource internal constructor(
    private val snapshotLoader: () -> ManagerConnectionSnapshot,
) : ConnectionSnapshotSource {
    constructor(settingsManager: SettingsManager) : this(settingsManager::getConnectionSnapshot)

    override suspend fun load(): ConnectionSnapshotSourceResult = try {
        ConnectionSnapshotSourceResult.Available(snapshotLoader())
    } catch (error: CancellationException) {
        throw error
    } catch (_: RuntimeException) {
        ConnectionSnapshotSourceResult.Unavailable(ConnectionSnapshotSourceStatus.FAILED)
    }
}

class RemoteConnectionSnapshotSource internal constructor(
    private val snapshotLoader: suspend () -> ManagerRuntimeResult<ManagerConnectionSnapshotDto>,
) : ConnectionSnapshotSource {
    constructor(client: ManagerRuntimeClient) : this(client::getConnectionSnapshot)

    override suspend fun load(): ConnectionSnapshotSourceResult = try {
        when (val result = snapshotLoader()) {
            is ManagerRuntimeResult.Success -> ConnectionSnapshotSourceResult.Available(
                result.value.toManagerConnectionSnapshot(),
            )

            is ManagerRuntimeResult.Unsupported -> ConnectionSnapshotSourceResult.Unavailable(
                ConnectionSnapshotSourceStatus.UNSUPPORTED,
            )

            is ManagerRuntimeResult.Unavailable -> ConnectionSnapshotSourceResult.Unavailable(
                result.availability.toConnectionSnapshotSourceStatus(),
            )

            is ManagerRuntimeResult.Failed -> ConnectionSnapshotSourceResult.Unavailable(
                ConnectionSnapshotSourceStatus.FAILED,
            )
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: RuntimeException) {
        ConnectionSnapshotSourceResult.Unavailable(ConnectionSnapshotSourceStatus.FAILED)
    }
}

class ComparingConnectionSnapshotSource(
    private val inProcessSource: ConnectionSnapshotSource,
    private val remoteSource: ConnectionSnapshotSource,
    private val enableRemoteCompare: Boolean = true,
) {
    suspend fun loadPrimary(): ConnectionSnapshotSourceResult = inProcessSource.load()

    suspend fun compareRemote(primary: ManagerConnectionSnapshot): ConnectionSnapshotComparison {
        if (!enableRemoteCompare) return ConnectionSnapshotComparison.NotStarted
        return when (val remote = remoteSource.load()) {
            is ConnectionSnapshotSourceResult.Available -> compareSnapshots(primary, remote.snapshot)
            is ConnectionSnapshotSourceResult.Unavailable -> ConnectionSnapshotComparison.Skipped(remote.status)
        }
    }
}

sealed interface ConnectionSnapshotComparison {
    data object NotStarted : ConnectionSnapshotComparison
    data object Comparing : ConnectionSnapshotComparison
    data object Matched : ConnectionSnapshotComparison

    data class Different(
        val fields: Set<ConnectionSnapshotField>,
    ) : ConnectionSnapshotComparison

    data class Skipped(
        val status: ConnectionSnapshotSourceStatus,
    ) : ConnectionSnapshotComparison
}

enum class ConnectionSnapshotField {
    CONNECTION_STATE,
    CONNECTED_AT_MS,
    LAST_DISCONNECTED_AT_MS,
    CONNECTION_SESSION_COUNT,
    SERVER_HOST,
    SERVER_IP,
    KEEP_ALIVE_INTERVAL_MS,
    PING_INTERVAL_MS,
    DOWNSTREAM_MESSAGE_COUNT,
    DELIVERED_TO_APP_COUNT,
    DUPLICATE_MESSAGE_COUNT,
    ACK_MESSAGE_COUNT,
    REGISTERED_PACKAGE_COUNT,
    TRACKED_CHANNEL_COUNT,
    BOUND_CHANNEL_COUNT,
}

private fun compareSnapshots(
    primary: ManagerConnectionSnapshot,
    remote: ManagerConnectionSnapshot,
): ConnectionSnapshotComparison {
    val fields = buildSet {
        if (primary.connectionState != remote.connectionState) add(ConnectionSnapshotField.CONNECTION_STATE)
        if (primary.connectedAtMs != remote.connectedAtMs) add(ConnectionSnapshotField.CONNECTED_AT_MS)
        if (primary.lastDisconnectedAtMs != remote.lastDisconnectedAtMs) {
            add(ConnectionSnapshotField.LAST_DISCONNECTED_AT_MS)
        }
        if (primary.connectionSessionCount != remote.connectionSessionCount) {
            add(ConnectionSnapshotField.CONNECTION_SESSION_COUNT)
        }
        if (primary.serverHost != remote.serverHost) add(ConnectionSnapshotField.SERVER_HOST)
        if (primary.serverIp != remote.serverIp) add(ConnectionSnapshotField.SERVER_IP)
        if (primary.keepAliveIntervalMs != remote.keepAliveIntervalMs) {
            add(ConnectionSnapshotField.KEEP_ALIVE_INTERVAL_MS)
        }
        if (primary.pingIntervalMs != remote.pingIntervalMs) add(ConnectionSnapshotField.PING_INTERVAL_MS)
        if (primary.downstreamMessageCount != remote.downstreamMessageCount) {
            add(ConnectionSnapshotField.DOWNSTREAM_MESSAGE_COUNT)
        }
        if (primary.deliveredToAppCount != remote.deliveredToAppCount) {
            add(ConnectionSnapshotField.DELIVERED_TO_APP_COUNT)
        }
        if (primary.duplicateMessageCount != remote.duplicateMessageCount) {
            add(ConnectionSnapshotField.DUPLICATE_MESSAGE_COUNT)
        }
        if (primary.ackMessageCount != remote.ackMessageCount) add(ConnectionSnapshotField.ACK_MESSAGE_COUNT)
        if (primary.registeredPackageCount != remote.registeredPackageCount) {
            add(ConnectionSnapshotField.REGISTERED_PACKAGE_COUNT)
        }
        if (primary.trackedChannelCount != remote.trackedChannelCount) {
            add(ConnectionSnapshotField.TRACKED_CHANNEL_COUNT)
        }
        if (primary.boundChannelCount != remote.boundChannelCount) {
            add(ConnectionSnapshotField.BOUND_CHANNEL_COUNT)
        }
    }
    return if (fields.isEmpty()) {
        ConnectionSnapshotComparison.Matched
    } else {
        ConnectionSnapshotComparison.Different(fields)
    }
}

private fun ManagerConnectionSnapshotDto.toManagerConnectionSnapshot(): ManagerConnectionSnapshot =
    ManagerConnectionSnapshot(
        connectionState = connectionState,
        connectedAtMs = connectedAtMs,
        lastDisconnectedAtMs = lastDisconnectedAtMs,
        connectionSessionCount = connectionSessionCount,
        serverHost = serverHost,
        serverIp = serverIp,
        keepAliveIntervalMs = keepAliveIntervalMs,
        pingIntervalMs = pingIntervalMs,
        downstreamMessageCount = downstreamMessageCount,
        deliveredToAppCount = deliveredToAppCount,
        duplicateMessageCount = duplicateMessageCount,
        ackMessageCount = ackMessageCount,
        registeredPackageCount = registeredPackageCount,
        trackedChannelCount = trackedChannelCount,
        boundChannelCount = boundChannelCount,
    )

private fun ManagerRuntimeAvailability.toConnectionSnapshotSourceStatus(): ConnectionSnapshotSourceStatus =
    when (this) {
        ManagerRuntimeAvailability.Disconnected -> ConnectionSnapshotSourceStatus.DISCONNECTED
        ManagerRuntimeAvailability.Binding -> ConnectionSnapshotSourceStatus.BINDING
        ManagerRuntimeAvailability.RuntimeMissing -> ConnectionSnapshotSourceStatus.RUNTIME_MISSING
        ManagerRuntimeAvailability.PermissionDenied -> ConnectionSnapshotSourceStatus.PERMISSION_DENIED
        ManagerRuntimeAvailability.TimedOut -> ConnectionSnapshotSourceStatus.TIMED_OUT
        is ManagerRuntimeAvailability.Incompatible -> ConnectionSnapshotSourceStatus.INCOMPATIBLE
        is ManagerRuntimeAvailability.TemporarilyDisconnected ->
            ConnectionSnapshotSourceStatus.TEMPORARILY_DISCONNECTED

        is ManagerRuntimeAvailability.Available,
        is ManagerRuntimeAvailability.Failed,
        -> ConnectionSnapshotSourceStatus.FAILED
    }
