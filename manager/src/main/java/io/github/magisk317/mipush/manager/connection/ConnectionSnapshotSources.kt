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

/** Gateway/Settings-backed snapshot source retained for tests and non-remote harnesses. */
class GatewayConnectionSnapshotSource internal constructor(
    private val snapshotLoader: suspend () -> ManagerConnectionSnapshot,
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
        frameworkRegistered = frameworkRegistered,
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
