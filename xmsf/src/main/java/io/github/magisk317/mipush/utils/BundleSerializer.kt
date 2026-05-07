package io.github.magisk317.mipush.utils

import android.os.Bundle
import android.os.Parcelable
import android.util.Base64
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Custom serializer for Android [Bundle] using kotlinx.serialization.
 *
 * Design goals:
 * 1) Keep logs readable.
 * 2) Preserve value types (especially arrays/lists/numbers) to avoid mismatches.
 * 3) Remain backward compatible with legacy untyped JSON produced by older versions.
 */
object BundleSerializer : KSerializer<Bundle> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("android.os.Bundle")

    override fun serialize(encoder: Encoder, value: Bundle) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Can only be used with JSON")
        val jsonObject = buildJsonObject {
            for (key in value.keySet()) {
                val item = value.readAny(key)
                put(key, encodeValue(item))
            }
        }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    private fun encodeValue(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Int -> tagged("int", JsonPrimitive(value))
            is Long -> tagged("long", JsonPrimitive(value))
            is Double -> tagged("double", JsonPrimitive(value))
            is Float -> tagged("float", JsonPrimitive(value))
            is Short -> tagged("short", JsonPrimitive(value.toInt()))
            is Byte -> tagged("byte", JsonPrimitive(value.toInt()))
            is Char -> tagged("char", JsonPrimitive(value.toString()))
            is String -> JsonPrimitive(value)
            is CharSequence -> tagged("charSequence", JsonPrimitive(value.toString()))
            is Bundle -> encodeBundle(value)
            is BooleanArray -> tagged("boolean[]", JsonArray(value.map { JsonPrimitive(it) }))
            is IntArray -> tagged("int[]", JsonArray(value.map { JsonPrimitive(it) }))
            is LongArray -> tagged("long[]", JsonArray(value.map { JsonPrimitive(it) }))
            is DoubleArray -> tagged("double[]", JsonArray(value.map { JsonPrimitive(it) }))
            is FloatArray -> tagged("float[]", JsonArray(value.map { JsonPrimitive(it) }))
            is ShortArray -> tagged("short[]", JsonArray(value.map { JsonPrimitive(it.toInt()) }))
            is ByteArray -> tagged("byte[]", JsonPrimitive(Base64.encodeToString(value, Base64.NO_WRAP)))
            is CharArray -> tagged("char[]", JsonArray(value.map { JsonPrimitive(it.toString()) }))
            is Array<*> -> tagged(
                "array",
                JsonArray(value.map { encodeValue(it) }),
                value.javaClass.componentType?.name
            )
            is List<*> -> tagged("list", JsonArray(value.map { encodeValue(it) }))
            is Map<*, *> -> tagged(
                "map",
                buildJsonObject {
                    value.entries.forEach { (k, v) ->
                        put(k.toString(), encodeValue(v))
                    }
                }
            )
            is Parcelable -> tagged("parcelable", JsonPrimitive(value.toString()), value.javaClass.name)
            is java.io.Serializable -> tagged("serializable", JsonPrimitive(value.toString()), value.javaClass.name)
            else -> tagged("unknown", JsonPrimitive(value.toString()), value.javaClass.name)
        }
    }

    override fun deserialize(decoder: Decoder): Bundle {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can only be used with JSON")
        val jsonElement = jsonDecoder.decodeJsonElement()
        return decodeBundle(jsonElement)
    }

    private fun encodeBundle(bundle: Bundle): JsonObject {
        return buildJsonObject {
            for (key in bundle.keySet()) {
                put(key, encodeValue(bundle.readAny(key)))
            }
        }
    }

    private fun decodeBundle(element: JsonElement): Bundle {
        val bundle = Bundle()
        if (element is JsonObject) {
            for ((key, raw) in element) {
                putDecoded(bundle, key, raw)
            }
        }
        return bundle
    }

    private fun putDecoded(bundle: Bundle, key: String, raw: JsonElement) {
        when (raw) {
            JsonNull -> bundle.putString(key, null)
            is JsonPrimitive -> putPrimitive(bundle, key, raw)
            is JsonArray -> {
                // Legacy format compatibility: old serializer wrote arrays as string array list.
                bundle.putStringArrayList(
                    key,
                    ArrayList(raw.map { (it as? JsonPrimitive)?.content ?: it.toString() })
                )
            }
            is JsonObject -> {
                val type = raw[TYPE_KEY]?.jsonPrimitive?.contentOrNull
                if (type == null) {
                    // Legacy nested object compatibility: interpret as nested Bundle.
                    bundle.putBundle(key, decodeBundle(raw))
                    return
                }
                val payload = raw[VALUE_KEY]
                when (type) {
                    "int" -> payload?.jsonPrimitive?.intOrNull?.let { bundle.putInt(key, it) }
                    "long" -> payload?.jsonPrimitive?.longOrNull?.let { bundle.putLong(key, it) }
                    "double" -> payload?.jsonPrimitive?.doubleOrNull?.let { bundle.putDouble(key, it) }
                    "float" -> payload?.jsonPrimitive?.contentOrNull?.toFloatOrNull()
                        ?.let { bundle.putFloat(key, it) }
                    "short" -> payload?.jsonPrimitive?.intOrNull?.let { bundle.putShort(key, it.toShort()) }
                    "byte" -> payload?.jsonPrimitive?.intOrNull?.let { bundle.putByte(key, it.toByte()) }
                    "char" -> payload?.jsonPrimitive?.contentOrNull?.firstOrNull()?.let { bundle.putChar(key, it) }
                    "charSequence" -> payload?.jsonPrimitive?.contentOrNull?.let { bundle.putCharSequence(key, it) }
                    "boolean[]" -> payload?.jsonArray?.let { arr ->
                        bundle.putBooleanArray(
                            key,
                            BooleanArray(arr.size) { idx ->
                                arr[idx].jsonPrimitive.boolean
                            }
                        )
                    }
                    "int[]" -> payload?.jsonArray?.let { arr ->
                        bundle.putIntArray(
                            key,
                            IntArray(arr.size) { idx ->
                                arr[idx].jsonPrimitive.int
                            }
                        )
                    }
                    "long[]" -> payload?.jsonArray?.let { arr ->
                        bundle.putLongArray(
                            key,
                            LongArray(arr.size) { idx ->
                                arr[idx].jsonPrimitive.long
                            }
                        )
                    }
                    "double[]" -> payload?.jsonArray?.let { arr ->
                        bundle.putDoubleArray(
                            key,
                            DoubleArray(arr.size) { idx ->
                                arr[idx].jsonPrimitive.double
                            }
                        )
                    }
                    "float[]" -> payload?.jsonArray?.let { arr ->
                        bundle.putFloatArray(
                            key,
                            FloatArray(arr.size) { idx ->
                                arr[idx].jsonPrimitive.content.toFloatOrNull() ?: 0f
                            }
                        )
                    }
                    "short[]" -> payload?.jsonArray?.let { arr ->
                        bundle.putShortArray(
                            key,
                            ShortArray(arr.size) { idx ->
                                arr[idx].jsonPrimitive.int.toShort()
                            }
                        )
                    }
                    "byte[]" -> payload?.jsonPrimitive?.contentOrNull?.let {
                        runCatching { Base64.decode(it, Base64.DEFAULT) }
                            .onSuccess { bytes -> bundle.putByteArray(key, bytes) }
                    }
                    "char[]" -> payload?.jsonArray?.let { arr ->
                        bundle.putCharArray(
                            key,
                            CharArray(arr.size) { idx ->
                                arr[idx].jsonPrimitive.content.firstOrNull() ?: '\u0000'
                            }
                        )
                    }
                    "array" -> payload?.jsonArray?.let { arr ->
                        bundle.putStringArrayList(
                            key,
                            ArrayList(arr.map { (it as? JsonPrimitive)?.content ?: it.toString() })
                        )
                    }
                    "list" -> payload?.jsonArray?.let { arr ->
                        bundle.putStringArrayList(
                            key,
                            ArrayList(arr.map { (it as? JsonPrimitive)?.content ?: it.toString() })
                        )
                    }
                    "map" -> payload?.jsonObject?.let { obj ->
                        bundle.putBundle(key, decodeBundle(obj))
                    }
                    "parcelable", "serializable", "unknown" -> {
                        // We cannot safely reconstruct arbitrary Parcelable/Serializable from logs.
                        payload?.jsonPrimitive?.contentOrNull?.let { bundle.putString(key, it) }
                    }
                    else -> payload?.jsonPrimitive?.contentOrNull?.let { bundle.putString(key, it) }
                }
            }
        }
    }

    private fun putPrimitive(bundle: Bundle, key: String, primitive: JsonPrimitive) {
        if (primitive.isString) {
            bundle.putString(key, primitive.content)
            return
        }
        val content = primitive.content
        if (content == "true" || content == "false") {
            bundle.putBoolean(key, primitive.boolean)
            return
        }
        // Legacy numeric compatibility: infer from lexical form.
        when {
            content.contains('.') || content.contains('e', ignoreCase = true) -> {
                primitive.doubleOrNull?.let { bundle.putDouble(key, it) }
            }
            else -> {
                val asLong = primitive.longOrNull
                if (asLong != null) {
                    if (asLong in Int.MIN_VALUE..Int.MAX_VALUE) {
                        bundle.putInt(key, asLong.toInt())
                    } else {
                        bundle.putLong(key, asLong)
                    }
                }
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun Bundle.readAny(key: String): Any? = get(key)

    private fun tagged(type: String, value: JsonElement, className: String? = null): JsonObject {
        return buildJsonObject {
            put(TYPE_KEY, JsonPrimitive(type))
            put(VALUE_KEY, value)
            if (!className.isNullOrBlank()) {
                put(CLASS_KEY, JsonPrimitive(className))
            }
        }
    }

    private const val TYPE_KEY = "__t"
    private const val VALUE_KEY = "__v"
    private const val CLASS_KEY = "__c"
}
