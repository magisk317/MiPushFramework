package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import java.util.UUID

internal object RemoteWriteSupport {
    /**
     * Preferred write entry for coroutine / suspend call sites (ViewModels, suspend gateways).
     */
    suspend fun execute(
        client: ManagerRuntimeClient,
        operation: String,
        packageName: String = "",
        userId: Int = 0,
        eventId: Long? = null,
        intArgument: Int = 0,
        longArgument: Long = 0L,
        booleanArgument: Boolean = false,
        argument: String = "",
        requestId: String? = null,
    ): ManagerWriteResultDto? {
        val resolvedRequestId = resolveRequestId(requestId)
        when (
            val result = client.executeWrite(
                ManagerWriteRequestDto(
                    requestId = resolvedRequestId,
                    operation = operation,
                    packageName = packageName,
                    userId = userId,
                    eventId = eventId,
                    intArgument = intArgument,
                    longArgument = longArgument,
                    booleanArgument = booleanArgument,
                    argument = argument,
                ),
            )
        ) {
            is ManagerRuntimeResult.Success -> return result.value
            else -> return fromRuntimeResult(resolvedRequestId, result)
        }
    }

    internal fun fromRuntimeResult(
        requestId: String,
        result: ManagerRuntimeResult<ManagerWriteResultDto>,
    ): ManagerWriteResultDto? = when (result) {
        is ManagerRuntimeResult.Success -> result.value
        is ManagerRuntimeResult.Unsupported -> ManagerWriteResultDto(
            requestId = requestId,
            status = ManagerProtocol.WRITE_STATUS_UNSUPPORTED,
            details = result.capability,
        )
        is ManagerRuntimeResult.Unavailable -> null
        is ManagerRuntimeResult.Failed -> ManagerWriteResultDto(
            requestId = requestId,
            status = ManagerProtocol.WRITE_STATUS_FAILED,
            details = result.reason,
        )
    }

    fun isSuccess(result: ManagerWriteResultDto?): Boolean =
        result != null &&
            (
                result.status == ManagerProtocol.WRITE_STATUS_SUCCESS ||
                    result.status == ManagerProtocol.WRITE_STATUS_DUPLICATE
                )

    fun requireSuccess(result: ManagerWriteResultDto?, operation: String): ManagerWriteResultDto {
        if (result == null) {
            throw RuntimeWriteUnavailableException(
                status = "runtime_unavailable",
                operation = operation,
            )
        }
        if (!isSuccess(result)) {
            throw RuntimeWriteRejectedException(
                status = result.status,
                operation = operation,
                details = result.details,
            )
        }
        return result
    }

    fun resolveRequestId(requestId: String? = null): String =
        requestId?.takeIf(String::isNotBlank) ?: UUID.randomUUID().toString()
}
