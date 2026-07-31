package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import java.util.UUID
import kotlinx.coroutines.runBlocking

internal object RemoteWriteSupport {
    /**
     * Preferred write entry for coroutine / suspend call sites (ViewModels, suspend gateways).
     */
    suspend fun execute(
        client: ManagerRuntimeClient,
        operation: String,
        packageName: String = "",
        eventId: Long? = null,
        intArgument: Int = 0,
        longArgument: Long = 0L,
        booleanArgument: Boolean = false,
        argument: String = "",
        requestId: String? = null,
    ): ManagerWriteResultDto? {
        when (
            val result = client.executeWrite(
                ManagerWriteRequestDto(
                    requestId = resolveRequestId(requestId),
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
            is ManagerRuntimeResult.Success -> return result.value
            is ManagerRuntimeResult.Unsupported,
            is ManagerRuntimeResult.Unavailable,
            is ManagerRuntimeResult.Failed,
            -> return null
        }
    }

    /**
     * Compatibility bridge for remaining sync `Manager*Gateway` façades.
     * New code should call [execute] from a coroutine instead.
     */
    fun executeBlocking(
        client: ManagerRuntimeClient,
        operation: String,
        packageName: String = "",
        eventId: Long? = null,
        intArgument: Int = 0,
        longArgument: Long = 0L,
        booleanArgument: Boolean = false,
        argument: String = "",
        requestId: String? = null,
    ): ManagerWriteResultDto? = runBlocking {
        execute(
            client = client,
            operation = operation,
            packageName = packageName,
            eventId = eventId,
            intArgument = intArgument,
            longArgument = longArgument,
            booleanArgument = booleanArgument,
            argument = argument,
            requestId = requestId,
        )
    }

    fun isSuccess(result: ManagerWriteResultDto?): Boolean =
        result != null &&
            (
                result.status == ManagerProtocol.WRITE_STATUS_SUCCESS ||
                    result.status == ManagerProtocol.WRITE_STATUS_DUPLICATE
                )

    fun resolveRequestId(requestId: String? = null): String =
        requestId?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
}
