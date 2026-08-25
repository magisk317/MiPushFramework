package io.github.magisk317.mipush.manager.connection

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.remote.RemoteWriteSupport

fun interface ConnectionReconnectRequester {
    suspend fun requestReconnect(): Boolean
}

class RemoteConnectionReconnectRequester internal constructor(
    private val request: suspend () -> ManagerWriteResultDto?,
) : ConnectionReconnectRequester {
    constructor(client: ManagerRuntimeClient) : this(
        request = {
            RemoteWriteSupport.execute(
                client = client,
                operation = ManagerProtocol.WRITE_OP_XMPP_RECONNECT,
            )
        },
    )

    override suspend fun requestReconnect(): Boolean =
        RemoteWriteSupport.isSuccess(request())
}
