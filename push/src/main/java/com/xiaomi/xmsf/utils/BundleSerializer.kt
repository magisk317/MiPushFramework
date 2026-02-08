package com.xiaomi.xmsf.utils

import android.os.Bundle
import android.os.Parcelable
import kotlinx.serialization.KSerializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

/**
 * Custom serializer for Android [Bundle] using kotlinx.serialization.
 * Supports primitive types, Strings, and nested Bundles/Lists.
 */
object BundleSerializer : KSerializer<Bundle> {
    override val descriptor: SerialDescriptor = buildClassSerialDescriptor("android.os.Bundle")

    override fun serialize(encoder: Encoder, value: Bundle) {
        val jsonEncoder = encoder as? JsonEncoder ?: error("Can only be used with JSON")
        val jsonObject = buildJsonObject {
            for (key in value.keySet()) {
                val item = value.get(key)
                put(key, wrapValue(item))
            }
        }
        jsonEncoder.encodeJsonElement(jsonObject)
    }

    private fun wrapValue(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            is String -> JsonPrimitive(value)
            is Bundle -> JsonObject(value.keySet().associateWith { wrapValue(value.get(it)) })
            is List<*> -> JsonArray(value.map { wrapValue(it) })
            is Map<*, *> -> JsonObject(value.entries.associate { it.key.toString() to wrapValue(it.value) })
            is Parcelable -> JsonPrimitive("Parcelable:${value.javaClass.name}") // Fallback
            else -> JsonPrimitive(value.toString())
        }
    }

    override fun deserialize(decoder: Decoder): Bundle {
        val jsonDecoder = decoder as? JsonDecoder ?: error("Can only be used with JSON")
        val jsonElement = jsonDecoder.decodeJsonElement()
        return toBundle(jsonElement)
    }

    private fun toBundle(element: JsonElement): Bundle {
        val bundle = Bundle()
        if (element is JsonObject) {
            for ((key, value) in element) {
                when (value) {
                    is JsonPrimitive -> {
                        if (value.isString) {
                            bundle.putString(key, value.content)
                        } else {
                            // Try boolean or number
                            val content = value.content
                            when {
                                content == "true" || content == "false" -> bundle.putBoolean(key, content.toBoolean())
                                content.contains('.') -> bundle.putDouble(key, content.toDouble())
                                else -> bundle.putLong(key, content.toLong())
                            }
                        }
                    }
                    is JsonObject -> bundle.putBundle(key, toBundle(value))
                    is JsonArray -> {
                        // Very basic list support, typically used for strings or ints in this project
                        val list = value.map { (it as? JsonPrimitive)?.content ?: it.toString() }
                        bundle.putStringArrayList(key, ArrayList(list))
                    }
                    else -> {}
                }
            }
        }
        return bundle
    }
}
