package io.github.magisk317.mipush.manager.notification

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.remote.RemoteWriteSupport

class RemoteNotificationChannelCommand internal constructor(
    private val executeWrite: suspend (operation: String, packageName: String, argument: String) ->
    ManagerWriteResultDto?,
) {
    constructor(client: ManagerRuntimeClient) : this(
        executeWrite = { operation, packageName, argument ->
            RemoteWriteSupport.execute(
                client = client,
                operation = operation,
                packageName = packageName,
                argument = argument,
            )
        },
    )

    suspend fun delete(packageName: String, channelId: String): Boolean {
        val normalizedPackageName = packageName.trim()
        val normalizedChannelId = channelId.trim()
        if (normalizedPackageName.isEmpty() || normalizedChannelId.isEmpty()) return false

        return RemoteWriteSupport.isSuccess(
            executeWrite(
                ManagerProtocol.WRITE_OP_DELETE_NOTIFICATION_CHANNEL,
                normalizedPackageName,
                normalizedChannelId,
            ),
        )
    }
}
