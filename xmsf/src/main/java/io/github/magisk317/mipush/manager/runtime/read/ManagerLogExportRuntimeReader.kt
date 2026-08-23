package io.github.magisk317.mipush.manager.runtime.read

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.magisk317.mipush.manager.api.ManagerLogExportResultDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.utils.LogBundleExporter
import io.github.magisk317.mipush.utils.DiagnosticExportModes
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import android.os.SystemClock
import java.io.File

/** Builds a runtime log bundle and returns a read-only ParcelFileDescriptor. */
class ManagerLogExportRuntimeReader(
    private val context: Context,
) {
    fun export(): ManagerLogExportResultDto {
        val started = SystemClock.elapsedRealtime()
        logI("ManagerRuntime exportRuntimeLogs start")
        val result = LogBundleExporter.buildLogBundle(
            context = context,
            mode = DiagnosticExportModes.fromDebugLoggingSetting(context),
        )
        val file = result.file
        val details = result.details.take(ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH)
        if (file == null || !file.isFile) {
            logW(
                "ManagerRuntime exportRuntimeLogs failed details=${details.ifBlank { "log_export_failed" }} " +
                    "tookMs=${SystemClock.elapsedRealtime() - started}",
            )
            return ManagerLogExportResultDto(
                success = false,
                details = details.ifBlank { "log_export_failed" },
                parcelFileDescriptor = null,
            )
        }
        val descriptor = openReadOnly(file)
        if (descriptor == null) {
            logW(
                "ManagerRuntime exportRuntimeLogs open_failed file=${file.absolutePath} " +
                    "tookMs=${SystemClock.elapsedRealtime() - started}",
            )
            return ManagerLogExportResultDto(
                success = false,
                details = "log_export_open_failed",
                parcelFileDescriptor = null,
            )
        }
        logI(
            "ManagerRuntime exportRuntimeLogs ok size=${file.length()} " +
                "tookMs=${SystemClock.elapsedRealtime() - started}",
        )
        return ManagerLogExportResultDto(
            success = true,
            details = details,
            parcelFileDescriptor = descriptor,
        )
    }

    private fun openReadOnly(file: File): ParcelFileDescriptor? = runCatching {
        ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }.getOrNull()
}
