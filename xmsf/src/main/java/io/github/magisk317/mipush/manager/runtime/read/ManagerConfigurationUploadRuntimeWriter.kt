package io.github.magisk317.mipush.manager.runtime.read

import android.content.Context
import android.os.ParcelFileDescriptor
import io.github.magisk317.mipush.common.configurations.ConfigJsonException
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadRequestDto
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadResultDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.utils.Configurations
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.charset.StandardCharsets

/**
 * Accepts a validated configuration document from the manager and atomically replaces the runtime
 * private active snapshot entry. A failed parse leaves the previous active snapshot intact.
 */
class ManagerConfigurationUploadRuntimeWriter(
    private val context: Context,
) {
    fun upload(request: ManagerConfigurationUploadRequestDto): ManagerConfigurationUploadResultDto {
        val descriptor = request.parcelFileDescriptor
            ?: return fail("configuration_upload_missing_descriptor")
        val path = request.path.substringAfterLast('/').ifBlank { request.path }
        if (!path.endsWith(".json", ignoreCase = true)) {
            return fail("configuration_upload_not_json")
        }
        val content = readLimited(descriptor, request.contentLength)
            ?: return fail("configuration_upload_read_failed")
        if (content.size > ManagerProtocol.MAX_CONFIGURATION_UPLOAD_BYTES) {
            return fail("configuration_upload_too_large")
        }
        val text = content.toString(StandardCharsets.UTF_8)
        val configurations = Configurations.getInstance()
        val previous = configurations.loader.getConfigs().mapValues { (_, value) ->
            value.toMutableList()
        }.toMutableMap()

        return try {
            try {
                configurations.load(text)
            } catch (_: ConfigJsonException) {
                restore(configurations, previous)
                return fail("configuration_upload_invalid_json")
            } catch (_: Exception) {
                restore(configurations, previous)
                return fail("configuration_upload_invalid_json")
            }

            val snapshotDir = File(context.filesDir, ACTIVE_CONFIG_DIR).apply { mkdirs() }
            val target = File(snapshotDir, path)
            val temp = File(snapshotDir, "$path.tmp")
            try {
                FileOutputStream(temp).use { it.write(content) }
                if (!temp.renameTo(target) && !(target.delete() && temp.renameTo(target))) {
                    temp.delete()
                    restore(configurations, previous)
                    return fail("configuration_upload_persist_failed")
                }
            } catch (_: Exception) {
                temp.delete()
                restore(configurations, previous)
                return fail("configuration_upload_persist_failed")
            }

            ManagerConfigurationUploadResultDto(
                success = true,
                activated = true,
                details = "activated:$path",
            )
        } finally {
            runCatching { descriptor.close() }
        }
    }

    private fun restore(
        configurations: Configurations,
        previous: MutableMap<String, MutableList<Any>>,
    ) {
        configurations.loader.getConfigs().clear()
        configurations.loader.getConfigs().putAll(previous)
    }

    private fun readLimited(descriptor: ParcelFileDescriptor, expectedLength: Int): ByteArray? =
        runCatching {
            FileInputStream(descriptor.fileDescriptor).use { input ->
                val limit = if (expectedLength > 0) {
                    expectedLength.coerceAtMost(ManagerProtocol.MAX_CONFIGURATION_UPLOAD_BYTES)
                } else {
                    ManagerProtocol.MAX_CONFIGURATION_UPLOAD_BYTES
                }
                val buffer = ByteArray(limit + 1)
                var offset = 0
                while (offset < buffer.size) {
                    val read = input.read(buffer, offset, buffer.size - offset)
                    if (read < 0) break
                    offset += read
                }
                if (offset > limit) return null
                buffer.copyOf(offset)
            }
        }.getOrNull()

    private fun fail(details: String) = ManagerConfigurationUploadResultDto(
        success = false,
        activated = false,
        details = details,
    )

    private companion object {
        const val ACTIVE_CONFIG_DIR = "manager_runtime_active_config"
    }
}
