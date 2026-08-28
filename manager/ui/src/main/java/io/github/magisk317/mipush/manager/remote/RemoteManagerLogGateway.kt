package io.github.magisk317.mipush.manager.remote

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Process
import io.github.magisk317.mipush.core.zygisk.ZygiskConfig
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.manager.application.ManagerApplicationDiagnostics
import io.github.magisk317.mipush.manager.application.ManagerApplicationGateway
import io.github.magisk317.mipush.manager.application.ManagerApplications
import io.github.magisk317.mipush.manager.application.ManagerDualAppInstallationResult
import io.github.magisk317.mipush.manager.application.ManagerConfigEditorSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigGateway
import io.github.magisk317.mipush.manager.application.ManagerConfigListSnapshot
import io.github.magisk317.mipush.manager.application.ManagerConfigSyncGateway
import io.github.magisk317.mipush.manager.application.ManagerConnectionSnapshot
import io.github.magisk317.mipush.manager.application.ManagerDayCount
import io.github.magisk317.mipush.manager.application.ManagerEvent
import io.github.magisk317.mipush.manager.application.ManagerForceRegisterResult
import io.github.magisk317.mipush.manager.application.EventDebugJson
import io.github.magisk317.mipush.manager.application.ManagerEventGateway
import io.github.magisk317.mipush.manager.application.ManagerLogClearResult
import io.github.magisk317.mipush.manager.application.ManagerLogExportResult
import io.github.magisk317.mipush.manager.application.ManagerLogGateway
import io.github.magisk317.mipush.manager.application.ManagerPermissionGateway
import io.github.magisk317.mipush.manager.application.ManagerRootAccessSnapshot
import io.github.magisk317.mipush.manager.application.ManagerRootAccessState
import io.github.magisk317.mipush.manager.application.ManagerRootSubjectStatus
import io.github.magisk317.mipush.manager.application.ManagerRootTarget
import io.github.magisk317.mipush.manager.application.ManagerRuntimeActions
import io.github.magisk317.mipush.manager.application.ManagerRuntimeEnvironmentSnapshot
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairResult
import io.github.magisk317.mipush.manager.application.ManagerXSpaceRepairStage
import io.github.magisk317.mipush.manager.application.ZygiskConfigGateway
import io.github.magisk317.mipush.manager.application.ZygiskConfigReadResult
import io.github.magisk317.mipush.manager.application.ZygiskModuleReadResult
import io.github.magisk317.mipush.manager.application.ZygiskPackageScanResult
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteResultDto
import io.github.magisk317.mipush.manager.application.ApplicationListRequest
import io.github.magisk317.mipush.manager.application.ApplicationReadResult
import io.github.magisk317.mipush.manager.application.RemoteApplicationDetailSource
import io.github.magisk317.mipush.manager.application.RemoteApplicationListSource
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.preferences.RuntimePreferenceGateway
import io.github.magisk317.mipush.manager.connection.ConnectionSnapshotSourceResult
import io.github.magisk317.mipush.manager.connection.RemoteConnectionSnapshotSource
import io.github.magisk317.mipush.manager.events.EventListRequest
import io.github.magisk317.mipush.manager.events.EventReadResult
import io.github.magisk317.mipush.manager.events.RemoteEventListSource
import io.github.magisk317.mipush.manager.logs.LogExportReadResult
import io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriter
import io.github.magisk317.mipush.manager.logs.ManagerLogBundleWriteResult
import io.github.magisk317.mipush.manager.logs.RemoteLogExportSource
import io.github.magisk317.mipush.core.configuration.LocalConfigSummary
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
import io.github.magisk317.mipush.manager.root.ManagerRootAccess
import java.util.UUID
import kotlinx.coroutines.flow.first
import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logW
import io.github.magisk317.xposed.logging.MagiskOtel

class RemoteManagerLogGateway(
    private val client: ManagerRuntimeClient,
    private val appContext: Context? = null,
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
                    runCatching { remoteTmp.delete() }
                    val details = listOf(dto.details, mergedDetail)
                        .filter { it.isNotBlank() }
                        .joinToString("; ")
                    ManagerLogExportResult(archivePath = outFile.absolutePath, details = details)
                } catch (@Suppress("TooGenericExceptionCaught") error: Exception) {
                    runCatching { descriptor.close() }
                    runCatching { remoteTmp.delete() }
                    runCatching { outFile.delete() }
                    ManagerLogExportResult(
                        archivePath = null,
                        details = "log_export_copy_failed:${error.message ?: error.javaClass.simpleName}",
                    )
                }
            }
            is LogExportReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("buildLogBundle", result.status)
                ManagerLogExportResult(
                    archivePath = null,
                    details = "runtime_log_export_unavailable:${result.status.name.lowercase()}",
                )
            }
        }
        emitManager(
            stage = "manager_log_export",
            result = if (export.archivePath != null) "ok" else "error",
            reason = if (export.archivePath != null) "exported" else "export_failed",
            statusOk = export.archivePath != null,
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
                        runCatching { descriptor.close() }
                        ManagerLogBundleWriteResult(
                            success = false,
                            details = "log_export_save_failed:${error.message ?: error.javaClass.simpleName}",
                        )
                    }
                }
            }
            is LogExportReadResult.Unavailable -> {
                RemoteRuntimeLog.unavailable("writeLogBundle", export.status)
                ManagerLogBundleWriteResult(
                    success = false,
                    details = "runtime_log_export_unavailable:${export.status.name.lowercase()}",
                )
            }
        }
        emitManager(
            stage = "manager_log_save",
            result = if (result.success) "ok" else "error",
            reason = if (result.success) "saved" else "save_failed",
            statusOk = result.success,
        )
        result
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

    private fun mergeRuntimeZipWithManagerLogs(
        context: Context,
        remoteInput: InputStream,
        destination: OutputStream,
    ): MergedRuntimeZipResult {
        val managerLogs = ManagerRuntimeFileLog.listExportableLogFiles(context)
        val seen = linkedSetOf<String>()
        val countedDestination = CountingOutputStream(destination)
        ZipInputStream(remoteInput.buffered()).use { input ->
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
                managerLogs.forEach { file ->
                    val name = "app/log/${file.name}"
                    if (name in seen) return@forEach
                    seen += name
                    output.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(output) }
                    output.closeEntry()
                }
            }
        }
        val details = if (managerLogs.isEmpty()) {
            "manager_logs:0"
        } else {
            "manager_logs:${managerLogs.size},bytes=${managerLogs.sumOf { it.length() }}"
        }
        return MergedRuntimeZipResult(
            details = "$details,destination_bytes=${countedDestination.bytesWritten}",
            bytesWritten = countedDestination.bytesWritten,
        )
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
                ?.forEach { runCatching { it.delete() } }
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
