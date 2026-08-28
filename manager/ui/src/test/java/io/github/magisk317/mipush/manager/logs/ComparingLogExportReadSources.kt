package io.github.magisk317.mipush.manager.logs

import android.content.Context
import io.github.magisk317.mipush.manager.application.ManagerLogExportResult

sealed interface LogExportComparison {
    data object Matched : LogExportComparison
    data class Mismatched(val fields: List<String>) : LogExportComparison
    data class Unavailable(val status: LogExportReadStatus) : LogExportComparison
}

class ComparingLogExportSource(
    private val primarySource: GatewayLogExportSource,
    private val remoteSource: RemoteLogExportSource,
    private val enableRemoteCompare: Boolean = false,
) {
    suspend fun exportPrimary(context: Context): ManagerLogExportResult = primarySource.export(context)

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
