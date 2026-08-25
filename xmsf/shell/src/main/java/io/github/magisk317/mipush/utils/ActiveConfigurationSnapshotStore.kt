package io.github.magisk317.mipush.utils

import android.content.Context
import io.github.magisk317.mipush.common.configurations.ConfigJsonException
import io.github.magisk317.xposed.logging.MagiskOtel
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Persistent manager-uploaded configuration snapshots under the runtime private files directory.
 * Applied as an overlay after SAF-tree loads so Binder uploads survive process restarts.
 */
object ActiveConfigurationSnapshotStore {
    const val ACTIVE_CONFIG_DIR = "manager_runtime_active_config"

    fun directory(context: Context): File =
        File(context.applicationContext.filesDir, ACTIVE_CONFIG_DIR)

    fun persist(context: Context, fileName: String, content: ByteArray): Boolean {
        val startedAt = System.nanoTime()
        val snapshotDir = directory(context).apply { mkdirs() }
        val target = File(snapshotDir, fileName)
        val ok = try {
            val temp = File.createTempFile(".active_config_", ".tmp", snapshotDir)
            try {
                temp.outputStream().use { it.write(content) }
                replaceFile(temp, target)
            } finally {
                if (temp.exists()) temp.delete()
            }
        } catch (_: Exception) {
            false
        }
        emitConfigSnapshot(
            stage = "persist",
            result = if (ok) "ok" else "error",
            reason = if (ok) "written" else "write_failed",
            durationMs = elapsedMs(startedAt),
            foundCount = 1,
            payloadSize = content.size,
            statusOk = ok,
        )
        return ok
    }

    /** Publish a complete snapshot without deleting the previous valid target first. */
    private fun replaceFile(temp: File, target: File): Boolean {
        return try {
            Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            true
        } catch (_: Exception) {
            runCatching {
                Files.move(temp.toPath(), target.toPath(), REPLACE_EXISTING)
                true
            }.getOrDefault(false)
        }
    }

    fun applyTo(
        context: Context,
        configurations: Configurations,
        target: MutableMap<String, MutableList<Any>>,
    ) {
        val startedAt = System.nanoTime()
        val dir = directory(context)
        if (!dir.isDirectory) {
            emitConfigSnapshot(
                stage = "apply",
                result = "skip",
                reason = "dir_missing",
                durationMs = elapsedMs(startedAt),
                foundCount = 0,
                statusOk = true,
            )
            return
        }
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        var loaded = 0
        var skipped = 0
        for (file in files) {
            val text = runCatching { file.readText() }.getOrNull()
            if (text == null) {
                skipped += 1
                continue
            }
            try {
                configurations.loader.loadInto(text, configurations, target)
                loaded += 1
            } catch (_: ConfigJsonException) {
                // Keep previous overlay entries; skip the corrupt file.
                skipped += 1
            } catch (_: Exception) {
                // Ignore unreadable snapshots so SAF/runtime config still loads.
                skipped += 1
            }
        }
        emitConfigSnapshot(
            stage = "apply",
            result = if (skipped == 0) "ok" else if (loaded > 0) "ok" else "skip",
            reason = if (files.isEmpty()) "empty" else if (skipped == 0) "loaded" else "partial",
            durationMs = elapsedMs(startedAt),
            foundCount = files.size,
            pendingCount = skipped,
            loadedCount = loaded,
            statusOk = true,
        )
    }

    private fun elapsedMs(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)

    private fun emitConfigSnapshot(
        stage: String,
        result: String,
        reason: String,
        durationMs: Long,
        foundCount: Int,
        pendingCount: Int? = null,
        loadedCount: Int? = null,
        payloadSize: Int? = null,
        statusOk: Boolean,
    ) {
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "xmsf",
            "stage" to "config_snapshot",
            "reason" to reason,
            "source" to stage,
            "found_count" to foundCount.toString(),
        )
        if (pendingCount != null) {
            attrs["pending_count"] = pendingCount.toString()
        }
        if (loadedCount != null) {
            attrs["change_count"] = loadedCount.toString()
        }
        if (payloadSize != null) {
            attrs["payload_size"] = payloadSize.toString()
        }
        MagiskOtel.event(name = "push.control", attributes = attrs, statusOk = statusOk)
    }
}
