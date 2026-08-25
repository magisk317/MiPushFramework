package io.github.magisk317.mipush.manager.runtime.read

import android.content.Context
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import io.github.magisk317.mipush.common.configurations.ConfigJsonException
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadRequestDto
import io.github.magisk317.mipush.manager.api.ManagerConfigurationUploadResultDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.utils.ActiveConfigurationSnapshotStore
import io.github.magisk317.mipush.utils.Configurations
import java.nio.charset.StandardCharsets
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Accepts a validated configuration document from the manager and atomically replaces the runtime
 * private active snapshot entry. A failed parse leaves the previous active snapshot intact.
 */
class ManagerConfigurationUploadRuntimeWriter(
    private val context: Context,
) {
    fun upload(request: ManagerConfigurationUploadRequestDto): ManagerConfigurationUploadResultDto {
        val startedAt = System.nanoTime()
        val descriptor = request.parcelFileDescriptor
            ?: return emitUploadFail(startedAt, "configuration_upload_missing_descriptor")
        return try {
            val path = request.path.substringAfterLast('/').ifBlank { request.path }
            if (!path.endsWith(".json", ignoreCase = true)) {
                return emitUploadFail(startedAt, "configuration_upload_not_json")
            }
            val content = readLimited(descriptor, request.contentLength)
                ?: return emitUploadFail(startedAt, "configuration_upload_read_failed")
            if (content.size > ManagerProtocol.MAX_CONFIGURATION_UPLOAD_BYTES) {
                return emitUploadFail(
                    startedAt,
                    "configuration_upload_too_large",
                    payloadSize = content.size,
                )
            }
            val text = content.toString(StandardCharsets.UTF_8)
            val configurations = Configurations.getInstance()
            val previous = configurations.loader.getConfigs().mapValues { (_, value) ->
                value.toMutableList()
            }.toMutableMap()

            try {
                configurations.load(text)
            } catch (_: ConfigJsonException) {
                restore(configurations, previous)
                return emitUploadFail(
                    startedAt,
                    "configuration_upload_invalid_json",
                    payloadSize = content.size,
                )
            } catch (_: Exception) {
                restore(configurations, previous)
                return emitUploadFail(
                    startedAt,
                    "configuration_upload_invalid_json",
                    payloadSize = content.size,
                )
            }

            if (!ActiveConfigurationSnapshotStore.persist(context, path, content)) {
                restore(configurations, previous)
                return emitUploadFail(
                    startedAt,
                    "configuration_upload_persist_failed",
                    payloadSize = content.size,
                )
            }

            MagiskOtel.event(
                name = "push.control",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to elapsedMs(startedAt).toString(),
                    "process" to "main",
                    "stage" to "config_upload",
                    "reason" to "activated",
                    "payload_size" to content.size.toString(),
                ),
                statusOk = true,
            )
            ManagerConfigurationUploadResultDto(
                success = true,
                activated = true,
                details = "activated:$path",
            )
        } finally {
            runCatching { descriptor.close() }
        }
    }

    private fun emitUploadFail(
        startedAt: Long,
        reason: String,
        payloadSize: Int? = null,
    ): ManagerConfigurationUploadResultDto {
        val attrs = mutableMapOf(
            "result" to "error",
            "duration_ms" to elapsedMs(startedAt).toString(),
            "process" to "main",
            "stage" to "config_upload",
            "reason" to reason,
        )
        if (payloadSize != null) {
            attrs["payload_size"] = payloadSize.toString()
        }
        MagiskOtel.event(name = "push.control", attributes = attrs, statusOk = false)
        return fail(reason)
    }

    private fun elapsedMs(startedAt: Long): Long =
        ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)

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
}
