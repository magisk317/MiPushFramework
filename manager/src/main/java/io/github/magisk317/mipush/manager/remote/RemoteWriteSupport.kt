package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import java.util.UUID
import kotlinx.coroutines.runBlocking

internal object RemoteWriteSupport {
    fun execute(
        client: ManagerRuntimeClient,
        operation: String,
        packageName: String = "",
        eventId: Long? = null,
        intArgument: Int = 0,
        longArgument: Long = 0L,
        booleanArgument: Boolean = false,
        argument: String = "",
    ): ManagerWriteResultDto? = runBlocking {
        when (
            val result = client.executeWrite(
                ManagerWriteRequestDto(
                    requestId = UUID.randomUUID().toString(),
                    operation = operation,
                    packageName = packageName,
                    eventId = eventId,
                    intArgument = intArgument,
                    longArgument = longArgument,
                    booleanArgument = booleanArgument,
                    argument = argument,
                ),
            )
        ) {
            is ManagerRuntimeResult.Success -> result.value
            is ManagerRuntimeResult.Unsupported,
            is ManagerRuntimeResult.Unavailable,
            is ManagerRuntimeResult.Failed,
            -> null
        }
    }

    fun isSuccess(result: ManagerWriteResultDto?): Boolean =
        result != null &&
            (
                result.status == ManagerProtocol.WRITE_STATUS_SUCCESS ||
                    result.status == ManagerProtocol.WRITE_STATUS_DUPLICATE
                )
}
