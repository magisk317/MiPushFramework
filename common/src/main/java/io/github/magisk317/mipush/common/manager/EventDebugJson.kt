package io.github.magisk317.mipush.common.manager

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * Pretty-printed debug JSON for manager event detail UI.
 *
 * The shared formatter is safe in both the in-process XMSF host and the standalone manager
 * process. Protocol-specific container details are added by the XMSF runtime gateway when it can
 * decode the payload; the standalone manager intentionally keeps only transport-safe fields.
 */
object EventDebugJson {
    private const val PAYLOAD_BASE64_PREVIEW_BYTES = 96

    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    private val receiveDateTimeFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS")

    fun format(event: ManagerEvent): String {
        return prettyJson.encodeToString(JsonElement.serializer(), toJsonElement(event))
    }

    fun toJsonElement(event: ManagerEvent): JsonElement = buildJsonObject {
        put("id", event.id)
        put("packageName", event.packageName)
        put("appName", event.appName?.let { JsonPrimitive(it) } ?: JsonNull)
        put("title", event.title)
        put("content", event.content)
        put("channel", event.channel.takeIf { it.isNotBlank() }?.let { JsonPrimitive(it) } ?: JsonNull)
        put(
            "configOptions",
            buildJsonArray {
                event.configOptions.sorted().forEach { add(JsonPrimitive(it)) }
            },
        )
        put(
            "receiveDate",
            Instant.ofEpochMilli(event.receiveDateMs)
                .atZone(ZoneId.systemDefault())
                .format(receiveDateTimeFormatter),
        )
        put("receiveDateMs", event.receiveDateMs)
        put("type", event.type)
        put("result", event.result)
        put("info", event.info?.let { JsonPrimitive(it) } ?: JsonNull)
        put("hasRegSec", !event.regSec.isNullOrBlank())
        val payload = event.payload
        if (payload == null) {
            put("payloadBytes", 0)
            put("payload", JsonNull)
            put("container", JsonNull)
        } else {
            put("payloadBytes", payload.size)
            val previewSlice = if (payload.size <= PAYLOAD_BASE64_PREVIEW_BYTES) payload else payload.copyOf(PAYLOAD_BASE64_PREVIEW_BYTES)
            put(
                "payloadBase64Preview",
                Base64.getEncoder().encodeToString(previewSlice) + if (payload.size > PAYLOAD_BASE64_PREVIEW_BYTES) "…" else "",
            )
            put(
                "container",
                buildJsonObject {
                    put("decode", "runtime_only")
                    put("note", "Protocol details are available from the XMSF runtime gateway")
                },
            )
        }
    }
}
