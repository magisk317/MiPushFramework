package io.github.magisk317.mipush.manager.logs

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.magisk317.mipush.common.manager.ManagerLogExportResult
import io.github.magisk317.mipush.common.manager.ManagerLogGateway
import io.github.magisk317.mipush.manager.api.ManagerLogExportResultDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileOutputStream

data class LogExportSnapshot(
    val success: Boolean,
    val details: String,
    val hasDescriptor: Boolean,
)

sealed interface LogExportReadResult<out T> {
    data class Available<T>(val value: T) : LogExportReadResult<T>
    data class Unavailable(val status: LogExportReadStatus) : LogExportReadResult<Nothing>
}

enum class LogExportReadStatus {
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

sealed interface LogExportComparison {
    data object Matched : LogExportComparison
    data class Mismatched(val fields: List<String>) : LogExportComparison
    data class Unavailable(val status: LogExportReadStatus) : LogExportComparison
}

class InProcessLogExportSource(
    private val logGateway: ManagerLogGateway,
) {
    fun export(context: Context): ManagerLogExportResult = logGateway.buildLogBundle(context)

    fun snapshot(context: Context): LogExportSnapshot {
        val result = export(context)
        return LogExportSnapshot(
            success = result.file != null,
            details = result.details,
            hasDescriptor = result.file != null,
        )
    }
}

class RemoteLogExportSource internal constructor(
    private val exporter: suspend () -> ManagerRuntimeResult<ManagerLogExportResultDto>,
) {
    constructor(client: ManagerRuntimeClient) : this(client::exportRuntimeLogs)

    suspend fun export(): LogExportReadResult<ManagerLogExportResultDto> = try {
        when (val result = exporter()) {
            is ManagerRuntimeResult.Success -> LogExportReadResult.Available(result.value)
            is ManagerRuntimeResult.Unsupported ->
                LogExportReadResult.Unavailable(LogExportReadStatus.UNSUPPORTED)
            is ManagerRuntimeResult.Unavailable ->
                LogExportReadResult.Unavailable(result.availability.toStatus())
            is ManagerRuntimeResult.Failed ->
                LogExportReadResult.Unavailable(LogExportReadStatus.FAILED)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: RuntimeException) {
        LogExportReadResult.Unavailable(LogExportReadStatus.FAILED)
    }

    suspend fun snapshot(): LogExportReadResult<LogExportSnapshot> = when (val result = export()) {
        is LogExportReadResult.Available -> {
            val hasDescriptor = result.value.parcelFileDescriptor != null
            // Close the remote descriptor immediately for comparison-only probes so we do not leak FDs.
            result.value.parcelFileDescriptor?.close()
            LogExportReadResult.Available(
                LogExportSnapshot(
                    success = result.value.success,
                    details = result.value.details,
                    hasDescriptor = hasDescriptor,
                ),
            )
        }
        is LogExportReadResult.Unavailable -> result
    }
}

class ComparingLogExportSource(
    private val primarySource: InProcessLogExportSource,
    private val remoteSource: RemoteLogExportSource,
    private val enableRemoteCompare: Boolean = false,
) {
    fun exportPrimary(context: Context): ManagerLogExportResult = primarySource.export(context)

    suspend fun compareRemote(primary: LogExportSnapshot): LogExportComparison {
        if (!enableRemoteCompare) return LogExportComparison.Matched
        return when (val remote = remoteSource.snapshot()) {
            is LogExportReadResult.Available -> compare(primary, remote.value)
            is LogExportReadResult.Unavailable -> LogExportComparison.Unavailable(remote.status)
        }
    }
}

private fun compare(primary: LogExportSnapshot, remote: LogExportSnapshot): LogExportComparison {
    val fields = mutableListOf<String>()
    if (primary.success != remote.success) fields += "success"
    if (primary.hasDescriptor != remote.hasDescriptor) fields += "descriptor"
    return if (fields.isEmpty()) {
        LogExportComparison.Matched
    } else {
        LogExportComparison.Mismatched(fields.distinct().sorted())
    }
}

private fun ManagerRuntimeAvailability.toStatus(): LogExportReadStatus = when (this) {
    is ManagerRuntimeAvailability.Disconnected -> LogExportReadStatus.DISCONNECTED
    is ManagerRuntimeAvailability.Binding -> LogExportReadStatus.BINDING
    is ManagerRuntimeAvailability.RuntimeMissing -> LogExportReadStatus.RUNTIME_MISSING
    is ManagerRuntimeAvailability.PermissionDenied -> LogExportReadStatus.PERMISSION_DENIED
    is ManagerRuntimeAvailability.TimedOut -> LogExportReadStatus.TIMED_OUT
    is ManagerRuntimeAvailability.Incompatible -> LogExportReadStatus.INCOMPATIBLE
    is ManagerRuntimeAvailability.TemporarilyDisconnected -> LogExportReadStatus.TEMPORARILY_DISCONNECTED
    is ManagerRuntimeAvailability.Failed -> LogExportReadStatus.FAILED
    is ManagerRuntimeAvailability.Available -> LogExportReadStatus.FAILED
}

/** Materialize a remote log descriptor into a local cache file for optional manager consumption. */
fun ManagerLogExportResultDto.materializeToFile(target: File): Boolean {
    val descriptor = parcelFileDescriptor ?: return false
    return try {
        descriptor.use { pfd ->
            FileOutputStream(target).use { output ->
                ParcelFileDescriptor.AutoCloseInputStream(pfd).use { input ->
                    input.copyTo(output)
                }
            }
        }
        true
    } catch (_: Exception) {
        false
    }
}
