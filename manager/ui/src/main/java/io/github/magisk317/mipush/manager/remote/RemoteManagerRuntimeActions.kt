package io.github.magisk317.mipush.manager.remote

import android.content.Context
import io.github.magisk317.mipush.manager.application.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRuntimeActions
import io.github.magisk317.mipush.manager.application.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceResult
import io.github.magisk317.mipush.manager.connection.RemoteConnectionSnapshotSource
import io.github.magisk317.mipush.common.utils.logW

class RemoteManagerRuntimeActions(
    private val client: ManagerRuntimeClient,
) : ManagerRuntimeActions {
    private val connectionSource = RemoteConnectionSnapshotSource(client)

    override suspend fun clearHistory() {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
            ),
            operation = ManagerProtocol.WRITE_OP_CLEAR_HISTORY,
        )
    }

    override suspend fun startMiPushServiceAsForegroundService(context: Context) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_START_FOREGROUND,
            ),
            operation = ManagerProtocol.WRITE_OP_START_FOREGROUND,
        )
    }

    override suspend fun resetTopActivityCache() {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_RESET_TOP_ACTIVITY_CACHE,
            ),
            operation = ManagerProtocol.WRITE_OP_RESET_TOP_ACTIVITY_CACHE,
        )
    }

    override suspend fun sendXmppReconnectRequest(context: Context): Boolean =
        RemoteWriteSupport.isSuccess(
            RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_XMPP_RECONNECT,
            ),
        )

    override suspend fun setXmppServer(context: Context, newHost: String) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_SET_XMPP_SERVER,
                argument = newHost,
            ),
            operation = ManagerProtocol.WRITE_OP_SET_XMPP_SERVER,
        )
    }

    override suspend fun getRuntimeEnvironmentSnapshot(context: Context): ManagerRuntimeEnvironmentSnapshot {
        return when (val result = client.getRuntimeEnvironmentSnapshot()) {
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Success ->
                ManagerRuntimeEnvironmentSnapshot(
                    isMiui = result.value.isMiui,
                    imei = result.value.imei,
                    macAddress = result.value.macAddress,
                    xmppServerHost = result.value.xmppServerHost,
                )
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Unsupported -> {
                logW("getRuntimeEnvironmentSnapshot unavailable status=unsupported")
                throw RuntimeReadUnavailableException("unsupported", "getRuntimeEnvironmentSnapshot")
            }
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Unavailable -> {
                logW("getRuntimeEnvironmentSnapshot unavailable status=${result.availability}")
                throw RuntimeReadUnavailableException(
                    result.availability.toString(),
                    "getRuntimeEnvironmentSnapshot",
                )
            }
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeResult.Failed -> {
                logW("getRuntimeEnvironmentSnapshot unavailable status=${result.reason}")
                throw RuntimeReadUnavailableException(result.reason, "getRuntimeEnvironmentSnapshot")
            }
        }
    }

    override suspend fun getConnectionSnapshot(): ManagerConnectionSnapshot {
        return when (val result = connectionSource.load()) {
            is ConnectionSnapshotSourceResult.Available -> result.snapshot
            is ConnectionSnapshotSourceResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("getConnectionSnapshot", result.status)
                throw RuntimeReadUnavailableException(
                    status = result.status.name,
                    operation = "getConnectionSnapshot",
                )
            }
        }
    }

    override fun observeNotificationEvent(packageName: String, action: String, source: String) = Unit

    override suspend fun setRuntimeLogRetentionDays(days: Int) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_SET_RUNTIME_LOG_RETENTION,
                intArgument = days.coerceAtLeast(1),
            ),
            operation = ManagerProtocol.WRITE_OP_SET_RUNTIME_LOG_RETENTION,
        )
    }

    override suspend fun applyEventRetentionDays(days: Int) {
        RemoteWriteSupport.requireSuccess(
            result = RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_APPLY_EVENT_RETENTION,
                intArgument = days.coerceAtLeast(1),
            ),
            operation = ManagerProtocol.WRITE_OP_APPLY_EVENT_RETENTION,
        )
    }

}
