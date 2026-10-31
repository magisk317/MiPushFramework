package io.github.magisk317.mipush.manager.remote

import android.content.Context
import io.github.magisk317.mipush.manager.application.ManagerLogClearResult
import io.github.magisk317.mipush.manager.application.ManagerLogExportResult
import io.github.magisk317.mipush.manager.application.ManagerLogGateway
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.logs.LogExportReadResult
import io.github.magisk317.mipush.manager.logs.LogExportReadStatus
import io.github.magisk317.mipush.manager.logs.ManagerLocalDiagnostics
import io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriter
import io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriteResult
import io.github.magisk317.mipush.manager.logs.RemoteLogExportSource
import io.github.magisk317.mipush.manager.root.ManagerRootAccess
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipOutputStream
import java.util.zip.ZipInputStream
import java.util.zip.ZipEntry
import java.util.zip.Deflater
import java.util.Locale
import java.util.Date
import java.text.SimpleDateFormat
import io.github.magisk317.mipush.manager.logging.ManagerRuntimeFileLog
import java.util.UUID
import io.github.magisk317.mipush.common.utils.logW

class RemoteManagerLogGateway(
    private val client: ManagerRuntimeClient,
    private val appContext: Context? = null,
    private val rootAccess: ManagerRootAccess? = null,
) : ManagerLogGateway, ManagerLogBundleWriter {
    private val exportSource = RemoteLogExportSource(client)

    override suspend fun setRetentionDays(days: Int) {
        val keepDays = days.coerceAtLeast(1)
        ManagerRuntimeFileLog.setRetentionDays(keepDays)
        pruneLocalManagerLogArtifacts(keepDays)
        // Runtime retention is written once by RemoteManagerRuntimeActions before
        // this local manager-log maintenance step.
    }

    /**
     * Best-effort local cleanup on the manager package: share temp zips and any residual
     * diagnostic dirs. Runtime-owned files/xmsf_logs still prune on the xmsf side.
     */
    private fun pruneLocalManagerLogArtifacts(keepDays: Int) {
        val context = appContext ?: return
        val cutoff = java.util.Calendar.getInstance(java.util.Locale.US).apply {
            timeInMillis = System.currentTimeMillis()
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
            add(java.util.Calendar.DAY_OF_YEAR, -(keepDays - 1))
        }.timeInMillis
        val targets = buildList {
            add(context.cacheDir)
            add(File(context.filesDir, "log"))
            add(File(context.filesDir, "crash"))
            add(File(context.filesDir, "private_export"))
            add(File(context.cacheDir, "log"))
            add(File(context.cacheDir, "logs"))
        }
        targets.forEach { root ->
            if (!root.exists()) return@forEach
            root.walkBottomUp().forEach { file ->
                if (file == root && file.isDirectory) return@forEach
                val mtime = file.lastModified()
                if (mtime > 0L && mtime < cutoff) {
                    runCatching {
                        if (file.isDirectory) {
                            if (file.listFiles().isNullOrEmpty()) file.delete()
                        } else {
                            file.delete()
                        }
                    }.onFailure {
                        // Deleting during a prune is best-effort: a busy file stays for the next run.
                    }
                }
            }
        }
    }

    override suspend fun buildLogBundle(context: Context): ManagerLogExportResult = run {
        val export = when (val result = exportSource.export()) {
            is LogExportReadResult.Available -> {
                val dto = result.value
                val descriptor = dto.parcelFileDescriptor
                if (!dto.success || descriptor == null) {
                    return@run ManagerLogExportResult(archivePath = null, details = dto.details).also {
                        emitManager(
                            stage = "manager_log_export",
                            result = "error",
                            reason = "descriptor_missing",
                            statusOk = false,
                        )
                    }
                }
                // Restore pre-split share name: mipush_logs_<timestamp>.zip (not UUID).
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val remoteTmp = File(context.cacheDir, ".runtime-export-$timestamp.zip")
                val outFile = File(context.cacheDir, "mipush_logs_$timestamp.zip")
                try {
                    FileOutputStream(remoteTmp).use { output ->
                        ParcelFileDescriptorAutoClose(descriptor).use { input ->
                            input.copyTo(output)
                        }
                    }
                    val mergedDetail = mergeRuntimeZipWithManagerLogs(
                        context = context,
                        remoteZip = remoteTmp,
                        outZip = outFile,
                    )
                    runCatching { remoteTmp.delete() }.onFailure {
                        // The temp came out of the export; a leftover is swept by the next clear.
                    }
                    val details = listOf(dto.details, mergedDetail)
                        .filter { it.isNotBlank() }
                        .joinToString("; ")
                    ManagerLogExportResult(archivePath = outFile.absolutePath, details = details)
                } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                    runCatching { descriptor.close() }.onFailure {
                        // Best-effort: the copy already failed and the descriptor leaves with it.
                    }
                    runCatching { remoteTmp.delete() }.onFailure {
                        // Same here; a leftover temp is picked up by the next clear.
                    }
                    runCatching { outFile.delete() }.onFailure {
                        // A half-written archive stays put rather than retrying inside the failure path.
                    }
                    ManagerLogExportResult(
                        archivePath = null,
                        details = "log_export_copy_failed:${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
            is LogExportReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("buildLogBundle", result.status)
                val note = unavailableNote(result.status)
                val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
                val outFile = File(context.cacheDir, "mipush_logs_$timestamp.zip")
                val local = runCatching {
                    FileOutputStream(outFile).use { output -> writeManagerLocalBundle(context, output, note) }
                }.getOrElse { error ->
                    ManagerLogBundleWriteResult(
                        success = false,
                        details = "manager_local_bundle_failed:${error.message ?: error.javaClass.simpleName}",
                    )
                }
                if (!local.success) runCatching { outFile.delete() }.onFailure {
                    // The write outcome is already reported; deleting again would not change it.
                }
                ManagerLogExportResult(
                    archivePath = outFile.absolutePath.takeIf { local.success },
                    details = "$note; ${local.details}",
                )
            }
        }
        val archived = export.archivePath != null
        emitManager(
            stage = "manager_log_export",
            result = if (archived) "ok" else "error",
            reason = when {
                !archived -> "export_failed"
                export.details.contains(RUNTIME_UNAVAILABLE_MARKER) -> "exported_manager_local_only"
                else -> "exported"
            },
            statusOk = archived,
        )
        export
    }

    override suspend fun writeLogBundle(
        context: Context,
        destination: OutputStream,
    ): ManagerLogBundleWriteResult = run {
        val result = when (val export = exportSource.export()) {
            is LogExportReadResult.Available -> {
                val dto = export.value
                val descriptor = dto.parcelFileDescriptor
                if (!dto.success || descriptor == null) {
                    ManagerLogBundleWriteResult(success = false, details = dto.details)
                } else {
                    try {
                        val merged = ParcelFileDescriptorAutoClose(descriptor).use { source ->
                            mergeRuntimeZipWithManagerLogs(context, source.inputStream(), destination)
                        }
                        if (merged.bytesWritten <= 0L) {
                            ManagerLogBundleWriteResult(
                                success = false,
                                details = "log_export_destination_empty",
                            )
                        } else {
                            ManagerLogBundleWriteResult(
                                success = true,
                                details = listOf(dto.details, merged.details)
                                    .filter { it.isNotBlank() }
                                    .joinToString("; "),
                            )
                        }
                    } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                        runCatching { descriptor.close() }.onFailure {
                            // The write outcome is recorded above; the descriptor is released with it.
                        }
                        ManagerLogBundleWriteResult(
                            success = false,
                            details = "log_export_save_failed:${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                }
            }
            is LogExportReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("writeLogBundle", export.status)
                val note = unavailableNote(export.status)
                val local = runCatching {
                    writeManagerLocalBundle(context, destination, note)
                }.getOrElse { error ->
                    ManagerLogBundleWriteResult(
                        success = false,
                        details = "manager_local_bundle_failed:${error.message ?: error.javaClass.simpleName}",
                    )
                }
                ManagerLogBundleWriteResult(success = local.success, details = "$note; ${local.details}")
            }
        }
        emitManager(
            stage = "manager_log_save",
            result = if (result.success) "ok" else "error",
            reason = when {
                !result.success -> "save_failed"
                result.details.contains(RUNTIME_UNAVAILABLE_MARKER) -> "saved_manager_local_only"
                else -> "saved"
            },
            statusOk = result.success,
        )
        result
    }

    private fun unavailableNote(status: LogExportReadStatus): String =
        "$RUNTIME_UNAVAILABLE_MARKER:${status.name.lowercase()}"

    /**
     * Binder-free archive: manager-local FileAntilog files plus [ManagerLocalDiagnostics].
     *
     * Smaller than a runtime bundle and produced from the manager process alone, so the one
     * state that used to leave the user with nothing to export - a runtime that never answers -
     * now still yields a file worth sending.
     */
    private fun writeManagerLocalBundle(
        context: Context,
        destination: OutputStream,
        note: String,
    ): ManagerLogBundleWriteResult {
        val counting = CountingOutputStream(destination)
        val summary = ZipOutputStream(counting.buffered()).use { output ->
            output.setLevel(Deflater.BEST_SPEED)
            appendManagerEntries(context, output, linkedSetOf(), note)
        }
        if (counting.bytesWritten <= 0L) {
            return ManagerLogBundleWriteResult(success = false, details = "manager_local_bundle_empty")
        }
        return ManagerLogBundleWriteResult(
            success = true,
            details = managerEntryDetails(summary, counting.bytesWritten),
        )
    }

    /**
     * Legacy archive-path fallback: materialize the remote ZIP and inject manager-local FileAntilog
     * jsonl under app/log/. Normal Manager UI saves instead stream this same merge into SAF.
     */
    private fun mergeRuntimeZipWithManagerLogs(
        context: Context,
        remoteZip: File,
        outZip: File,
    ): String = remoteZip.inputStream().buffered().use { input ->
        outZip.outputStream().buffered().use { output ->
            mergeRuntimeZipWithManagerLogs(context, input, output).details
        }
    }

    private data class MergedRuntimeZipResult(
        val details: String,
        val bytesWritten: Long,
    )

    private data class ManagerLogEntrySummary(
        val files: Int,
        val bytes: Long,
    )

    private fun mergeRuntimeZipWithManagerLogs(
        context: Context,
        remoteInput: InputStream,
        destination: OutputStream,
    ): MergedRuntimeZipResult {
        val seen = linkedSetOf<String>()
        val countedDestination = CountingOutputStream(destination)
        val summary = ZipInputStream(remoteInput.buffered()).use { input ->
            ZipOutputStream(countedDestination.buffered()).use { output ->
                output.setLevel(Deflater.BEST_SPEED)
                while (true) {
                    val entry = input.nextEntry ?: break
                    val name = entry.name
                    if (name.isBlank() || name in seen) {
                        input.closeEntry()
                        continue
                    }
                    seen += name
                    output.putNextEntry(ZipEntry(name))
                    input.copyTo(output)
                    output.closeEntry()
                    input.closeEntry()
                }
                appendManagerEntries(context, output, seen, RUNTIME_AVAILABLE_NOTE)
            }
        }
        return MergedRuntimeZipResult(
            details = managerEntryDetails(summary, countedDestination.bytesWritten),
            bytesWritten = countedDestination.bytesWritten,
        )
    }

    /**
     * Manager-owned archive entries: the host-side FileAntilog jsonl plus [ManagerLocalDiagnostics].
     *
     * Names already taken by the runtime archive win, so a bundle never carries two copies of the
     * same day under different prefixes.
     */
    private fun appendManagerEntries(
        context: Context,
        output: ZipOutputStream,
        seen: MutableSet<String>,
        note: String,
    ): ManagerLogEntrySummary {
        val managerLogs = ManagerRuntimeFileLog.listExportableLogFiles(context)
        managerLogs.forEach { file ->
            val name = "app/log/${file.name}"
            if (name in seen) return@forEach
            seen += name
            output.putNextEntry(ZipEntry(name))
            file.inputStream().use { it.copyTo(output) }
            output.closeEntry()
        }
        if (ManagerLocalDiagnostics.ENTRY_NAME !in seen) {
            seen += ManagerLocalDiagnostics.ENTRY_NAME
            output.putNextEntry(ZipEntry(ManagerLocalDiagnostics.ENTRY_NAME))
            output.write(
                ManagerLocalDiagnostics.build(
                    context = context,
                    availability = client.availability.value,
                    rootGrantCache = rootAccess?.cachedGrantState(),
                    logFiles = managerLogs,
                    note = note,
                ).toByteArray(Charsets.UTF_8),
            )
            output.closeEntry()
        }
        return ManagerLogEntrySummary(
            files = managerLogs.size,
            bytes = managerLogs.sumOf { it.length() },
        )
    }

    private fun managerEntryDetails(summary: ManagerLogEntrySummary, destinationBytes: Long): String {
        val prefix = if (summary.files == 0) {
            "manager_logs:0"
        } else {
            "manager_logs:${summary.files},bytes=${summary.bytes}"
        }
        return "$prefix,destination_bytes=$destinationBytes"
    }


    override suspend fun clearLogFolders(context: Context): ManagerLogClearResult {
        // Clear manager-local diagnostic dirs best-effort.
        runCatching {
            ManagerRuntimeFileLog.clear(context)
            listOf("log", "crash", "private_export").forEach { name ->
                File(context.filesDir, name).deleteRecursively()
            }
            File(context.cacheDir, "log").deleteRecursively()
            // Legacy UUID share temps + current mipush_logs share temps.
            context.cacheDir.listFiles()
                ?.filter { it.isFile && (it.name.startsWith("runtime-log-") || it.name.startsWith("mipush_logs_") || it.name.startsWith(".runtime-export-")) }
                ?.forEach { temp ->
                    runCatching { temp.delete() }.onFailure {
                        // A share temp still held by the receiver is left for the next clear.
                    }
                }
        }.onFailure { error ->
            logW("clearing the local log folders failed: ${error.message}", error)
        }
        val result = RemoteWriteSupport.execute(
            client = client,
            operation = ManagerProtocol.WRITE_OP_CLEAR_LOG_FOLDERS,
        )
        val clearResult = if (RemoteWriteSupport.isSuccess(result)) {
            ManagerLogClearResult(
                success = true,
                details = result?.details.orEmpty().ifBlank { "clear_log_folders_ok" },
            )
        } else {
            ManagerLogClearResult(
                success = false,
                details = result?.details ?: "clear_logs_runtime_unavailable",
            )
        }
        emitManager(
            stage = "manager_log_clear",
            result = if (clearResult.success) "ok" else "error",
            reason = if (clearResult.success) "cleared" else "clear_failed",
            statusOk = clearResult.success,
        )
        return clearResult
    }
}

internal const val RUNTIME_UNAVAILABLE_MARKER = "runtime_log_export_unavailable"
private const val RUNTIME_AVAILABLE_NOTE = "runtime_log_export_available"
