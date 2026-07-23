package io.github.magisk317.mipush.utils

import android.content.Context
import io.github.magisk317.mipush.common.configurations.ConfigJsonException
import java.io.File

/**
 * Persistent manager-uploaded configuration snapshots under the runtime private files directory.
 * Applied as an overlay after SAF-tree loads so Binder uploads survive process restarts.
 */
object ActiveConfigurationSnapshotStore {
    const val ACTIVE_CONFIG_DIR = "manager_runtime_active_config"

    fun directory(context: Context): File =
        File(context.applicationContext.filesDir, ACTIVE_CONFIG_DIR)

    fun persist(context: Context, fileName: String, content: ByteArray): Boolean {
        val snapshotDir = directory(context).apply { mkdirs() }
        val target = File(snapshotDir, fileName)
        val temp = File(snapshotDir, "$fileName.tmp")
        return try {
            temp.outputStream().use { it.write(content) }
            if (temp.renameTo(target)) {
                true
            } else {
                target.delete()
                temp.renameTo(target)
            }
        } catch (_: Exception) {
            temp.delete()
            false
        }
    }

    fun applyTo(
        context: Context,
        configurations: Configurations,
        target: MutableMap<String, MutableList<Any>>,
    ) {
        val dir = directory(context)
        if (!dir.isDirectory) return
        val files = dir.listFiles()
            ?.filter { it.isFile && it.name.endsWith(".json", ignoreCase = true) }
            ?.sortedBy { it.name }
            .orEmpty()
        for (file in files) {
            val text = runCatching { file.readText() }.getOrNull() ?: continue
            try {
                configurations.loader.loadInto(text, configurations, target)
            } catch (_: ConfigJsonException) {
                // Keep previous overlay entries; skip the corrupt file.
            } catch (_: Exception) {
                // Ignore unreadable snapshots so SAF/runtime config still loads.
            }
        }
    }
}
