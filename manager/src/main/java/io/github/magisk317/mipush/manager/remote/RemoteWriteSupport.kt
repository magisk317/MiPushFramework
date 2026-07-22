package io.github.magisk317.mipush.manager.remote

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import java.security.MessageDigest
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
                    requestId = stableRequestId(
                        operation = operation,
                        packageName = packageName,
                        eventId = eventId,
                        intArgument = intArgument,
                        longArgument = longArgument,
                        booleanArgument = booleanArgument,
                        argument = argument,
                    ),
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

    /**
     * Derives a stable request id from the logical write so Binder-death retries reuse the same id
     * and hit the runtime idempotency store instead of repeating a destructive action.
     */
    fun stableRequestId(
        operation: String,
        packageName: String = "",
        eventId: Long? = null,
        intArgument: Int = 0,
        longArgument: Long = 0L,
        booleanArgument: Boolean = false,
        argument: String = "",
    ): String {
        val material = buildString {
            append(operation)
            append('\u0000')
            append(packageName)
            append('\u0000')
            append(eventId?.toString().orEmpty())
            append('\u0000')
            append(intArgument)
            append('\u0000')
            append(longArgument)
            append('\u0000')
            append(booleanArgument)
            append('\u0000')
            append(argument)
        }
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(material.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * HEX_CHARS_PER_BYTE) {
            for (byte in digest) {
                val value = byte.toInt() and 0xff
                append(HEX_DIGITS[value ushr 4])
                append(HEX_DIGITS[value and 0x0f])
            }
        }.take(ManagerProtocol.MAX_WRITE_REQUEST_ID_LENGTH)
    }

    private const val HEX_CHARS_PER_BYTE = 2
    private val HEX_DIGITS = "0123456789abcdef".toCharArray()
}
