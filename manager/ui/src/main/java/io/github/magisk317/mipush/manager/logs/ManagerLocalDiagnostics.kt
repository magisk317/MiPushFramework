package io.github.magisk317.mipush.manager.logs

import android.content.Context
import android.os.Build
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manager-side diagnostics block for a bundle the runtime Binder could not contribute to.
 *
 * Every value comes from inside the manager process: no Binder call and no root shell. That is
 * the point - when xmsf never came up, a Binder read is exactly the thing that cannot answer,
 * while the last availability the client published is exactly the fact the user needs to
 * communicate. The same block rides along in healthy bundles so one archive always explains
 * itself.
 */
object ManagerLocalDiagnostics {
    const val ENTRY_NAME: String = "diagnostics.txt"

    private val timestampFormat = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US)
    private val probeMarkers = listOf("root_probe", "xmsf_recovery")
    private const val MAX_PROBE_LINES = 24
    private const val MAX_SCANNED_LINES_PER_FILE = 4000
    private const val UNKNOWN = "unknown"

    fun build(
        context: Context,
        availability: ManagerRuntimeAvailability?,
        rootGrantCache: Boolean?,
        logFiles: List<File>,
        note: String,
    ): String = buildString {
        appendLine("generated_at=${timestampFormat.format(Date())}")
        appendLine("note=$note")
        appendLine("package=${context.packageName}")
        appendLine("manager_version=${versionLabel(context)}")
        appendLine("device=${Build.MANUFACTURER} ${Build.MODEL}")
        appendLine("android=${Build.VERSION.RELEASE} sdk=${Build.VERSION.SDK_INT}")
        appendLine("runtime_availability=${describe(availability)}")
        appendLine("root_grant_cache=${rootGrantCache?.toString() ?: "never_probed"}")
        appendLine("log_files=${logFiles.size}")
        logFiles.forEach { file ->
            appendLine(fileLine(file))
        }
        val probes = probeLines(logFiles)
        appendLine("recent_probe_lines=${probes.size}")
        probes.forEach { line ->
            appendLine("  $line")
        }
    }

    /**
     * Last state the client published, in the archive's own vocabulary.
     *
     * Kept separate from [build] because it is pure: the sealed hierarchy is the diagnosis, and
     * a regression here silently turns the block into useless prose.
     */
    internal fun describe(availability: ManagerRuntimeAvailability?): String = when (availability) {
        null -> "unobserved"
        ManagerRuntimeAvailability.Disconnected -> "disconnected"
        ManagerRuntimeAvailability.Binding -> "binding"
        ManagerRuntimeAvailability.RuntimeMissing -> "runtime_missing"
        ManagerRuntimeAvailability.PermissionDenied -> "permission_denied"
        ManagerRuntimeAvailability.TimedOut -> "timed_out"
        is ManagerRuntimeAvailability.Available -> "available runtime=${availability.handshake.runtimeVersionName}" +
            "(${availability.handshake.runtimeVersionCode}) protocol=${availability.handshake.protocolMajor}" +
            ".${availability.handshake.protocolMinor} commit=${availability.handshake.runtimeCommit ?: UNKNOWN}"
        is ManagerRuntimeAvailability.Incompatible -> "incompatible reason=${availability.reason} " +
            "runtime=${availability.handshake.runtimeVersionName}(${availability.handshake.runtimeVersionCode})"
        is ManagerRuntimeAvailability.TemporarilyDisconnected -> "temporarily_disconnected reason=${availability.reason}"
        is ManagerRuntimeAvailability.Failed -> "failed reason=${availability.reason}"
    }

    /**
     * Root-probe and XMSF-recovery lines already written by [io.github.magisk317.mipush.manager.root.ManagerRootAccess].
     *
     * They are pulled out of the daily jsonl because they are the two lines that distinguish
     * "root was never granted" from "root is fine but the runtime died": both look identical
     * from the UI. Already sanitized by the file logger, so no new redaction is applied here.
     */
    internal fun probeLines(logFiles: List<File>): List<String> {
        val matched = mutableListOf<String>()
        logFiles.forEach { file ->
            runCatching {
                file.bufferedReader().use { reader ->
                    reader.lineSequence()
                        .filter { line -> probeMarkers.any { marker -> line.contains(marker) } }
                        .take(MAX_SCANNED_LINES_PER_FILE)
                        .forEach { line -> matched += line.trim() }
                }
            }.onFailure {
                // An unreadable log file must not abort the scan of the remaining ones.
            }
        }
        return if (matched.size <= MAX_PROBE_LINES) matched else matched.takeLast(MAX_PROBE_LINES)
    }

    private fun fileLine(file: File): String =
        "  ${file.name} bytes=${file.length()} mtime=${timestampFormat.format(Date(file.lastModified()))}"

    private fun versionLabel(context: Context): String = runCatching {
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        "${info.versionName}(${info.longVersionCode})"
    }.getOrDefault(UNKNOWN)
}
