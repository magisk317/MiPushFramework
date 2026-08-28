package io.github.magisk317.mipush.platform.support

import android.content.Context
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Android file operations for runtime configuration snapshots.
 *
 * Parsing and applying a snapshot stays in the shell because it owns the configuration model;
 * this utility owns only the reusable private-files-directory and atomic-replacement mechanics.
 */
object ActiveConfigurationSnapshotFiles {
    fun directory(context: Context, directoryName: String): File =
        File(context.applicationContext.filesDir, directoryName)

    fun persist(
        context: Context,
        directoryName: String,
        fileName: String,
        content: ByteArray,
    ): Boolean {
        val snapshotDir = directory(context, directoryName).apply { mkdirs() }
        val target = File(snapshotDir, fileName)
        return try {
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
    }

    private fun replaceFile(temp: File, target: File): Boolean =
        try {
            Files.move(temp.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
            true
        } catch (_: Exception) {
            runCatching {
                Files.move(temp.toPath(), target.toPath(), REPLACE_EXISTING)
                true
            }.getOrDefault(false)
        }
}
