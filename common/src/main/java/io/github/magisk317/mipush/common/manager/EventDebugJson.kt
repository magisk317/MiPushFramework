package io.github.magisk317.mipush.common.manager

import android.util.Base64
import io.github.magisk317.mipush.common.configurations.XMPushUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import org.apache.thrift.TBase

/**
 * Pretty-printed debug JSON for manager event detail UI.
 *
 * Works in both the in-process xmsf host and the standalone manager process
 * (where thrift decrypt helpers are unavailable). When [ManagerEvent.payload]
 * is present, top-level container fields are decoded via [XMPushUtils].
 */
object EventDebugJson {
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
            put(
                "payloadBase64Preview",
                Base64.encodeToString(
                    payload,
                    0,
                    minOf(payload.size, 96),
                    Base64.NO_WRAP,
                ) + if (payload.size > 96) "…" else "",
            )
            put("container", containerSummary(payload))
        }
    }

    private fun containerSummary(payload: ByteArray): JsonElement {
        val container = XMPushUtils.packToContainer(payload) ?: return buildJsonObject {
            put("decode", "failed")
        }
        return thriftToJson(container)
    }

    private fun thriftToJson(base: TBase<*, *>, depth: Int = 0): JsonElement {
        if (depth > 3) {
            return JsonPrimitive(base.toString())
        }
        return buildJsonObject {
            put("_type", base.javaClass.simpleName)
            val methods = base.javaClass.methods
                .asSequence()
                .filter { method ->
                    method.parameterCount == 0 &&
                        method.name.startsWith("get") &&
                        method.name != "getClass" &&
                        method.name != "getFieldValue" &&
                        !method.name.startsWith("getSet") &&
                        java.lang.reflect.Modifier.isPublic(method.modifiers)
                }
                .sortedBy { it.name }
            for (method in methods) {
                val key = method.name.removePrefix("get").replaceFirstChar { it.lowercase() }
                if (key.isBlank() || key == "metaDataMap" || key == "fieldValue") continue
                val value = runCatching { method.invoke(base) }.getOrNull() ?: continue
                if (value === base) continue
                put(key, valueToJson(value, depth + 1))
            }
            // Boolean isX / isY style thrift accessors
            base.javaClass.methods
                .asSequence()
                .filter { method ->
                    method.parameterCount == 0 &&
                        method.name.startsWith("is") &&
                        method.returnType == java.lang.Boolean.TYPE &&
                        java.lang.reflect.Modifier.isPublic(method.modifiers)
                }
                .sortedBy { it.name }
                .forEach { method ->
                    val key = method.name
                    val value = runCatching { method.invoke(base) as? Boolean }.getOrNull()
                    if (value != null) {
                        put(key, value)
                    }
                }
        }
    }

    private fun valueToJson(value: Any?, depth: Int): JsonElement = when (value) {
        null -> JsonNull
        is String -> JsonPrimitive(value)
        is Boolean -> JsonPrimitive(value)
        is Number -> JsonPrimitive(value)
        is Enum<*> -> JsonPrimitive(value.name)
        is ByteArray -> JsonPrimitive(
            "byte[${value.size}]:" + Base64.encodeToString(
                value,
                0,
                minOf(value.size, 48),
                Base64.NO_WRAP,
            ) + if (value.size > 48) "…" else "",
        )
        is TBase<*, *> -> thriftToJson(value, depth)
        is Map<*, *> -> buildJsonObject {
            value.entries.take(64).forEach { (k, v) ->
                put(k?.toString() ?: "null", valueToJson(v, depth + 1))
            }
        }
        is Iterable<*> -> buildJsonArray {
            value.take(64).forEach { add(valueToJson(it, depth + 1)) }
        }
        is Array<*> -> buildJsonArray {
            value.take(64).forEach { add(valueToJson(it, depth + 1)) }
        }
        else -> JsonPrimitive(value.toString())
    }
}
