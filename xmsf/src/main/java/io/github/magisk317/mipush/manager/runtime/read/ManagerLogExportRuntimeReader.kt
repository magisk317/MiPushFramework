package io.github.magisk317.mipush.manager.runtime.read

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.magisk317.mipush.manager.api.ManagerLogExportResultDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.utils.LogBundleExporter
import java.io.File

/** Builds a runtime log bundle and returns a read-only ParcelFileDescriptor. */
class ManagerLogExportRuntimeReader(
    private val context: Context,
) {
    fun export(): ManagerLogExportResultDto {
        val result = LogBundleExporter.buildLogBundle(context)
        val file = result.file
        val details = result.details.take(ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH)
        if (file == null || !file.isFile) {
            return ManagerLogExportResultDto(
                success = false,
                details = details.ifBlank { "log_export_failed" },
                parcelFileDescriptor = null,
            )
        }
        val descriptor = openReadOnly(file)
            ?: return ManagerLogExportResultDto(
                success = false,
                details = "log_export_open_failed",
                parcelFileDescriptor = null,
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
