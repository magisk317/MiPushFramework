package io.github.magisk317.mipush.common.configurations

import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

class ConfigJsonException(message: String, cause: Throwable? = null) : Exception(message, cause)

object ConfigJson {
    private val compact = Json { ignoreUnknownKeys = true }
    private val pretty = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val integerLiteralPattern = Regex("-?(0|[1-9][0-9]*)")

    fun parse(text: String): Any? = convert(parseElement(text))

    fun parseObject(text: String): ConfigJsonObject {
        return ConfigJsonObject(parseElement(text).asObject())
    }

    internal fun parseElement(text: String): JsonElement {
        return try {
            compact.parseToJsonElement(text)
        } catch (e: SerializationException) {
            throw ConfigJsonException(e.message ?: "JSON parse error", e)
        } catch (e: IllegalArgumentException) {
            throw ConfigJsonException(e.message ?: "JSON parse error", e)
        }
    }

    internal fun convert(element: JsonElement?): Any? {
        return when (element) {
            null, JsonNull -> null
            is JsonObject -> ConfigJsonObject(element)
            is JsonArray -> ConfigJsonArray(element)
            is JsonPrimitive -> convertPrimitive(element)
        }
    }

    private fun convertPrimitive(element: JsonPrimitive): Any? {
        if (element.isString) {
            return element.content
        }

        element.booleanOrNull?.let { return it }
        if (integerLiteralPattern.matches(element.content)) {
            return element.longOrNull
                ?: throw ConfigJsonException("JSON integer is outside the Long range: ${element.content}")
        }

        val double = element.doubleOrNull
        if (double != null) {
            if (!double.isFinite()) {
                throw ConfigJsonException("JSON number is not finite: ${element.content}")
            }
            return double
        }
        return element.contentOrNull
    }

    internal fun toElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is ConfigJsonObject -> value.toElement()
            is ConfigJsonArray -> value.toElement()
            is JsonElement -> value
            is Boolean -> JsonPrimitive(value)
            is Double -> if (value.isFinite()) {
                JsonPrimitive(value)
            } else {
                throw ConfigJsonException("JSON number is not finite: $value")
            }
            is Float -> if (value.isFinite()) {
                JsonPrimitive(value)
            } else {
                throw ConfigJsonException("JSON number is not finite: $value")
            }
            is Number -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }

    internal fun toJsonString(element: JsonElement, indentSpaces: Int? = null): String {
        return if (indentSpaces == null || indentSpaces <= 0) {
            compact.encodeToString(JsonElement.serializer(), element)
        } else {
            pretty.encodeToString(JsonElement.serializer(), element)
        }
    }

    private fun JsonElement.asObject(): JsonObject {
        return this as? JsonObject ?: throw ConfigJsonException("JSON value is not an object")
    }
}

class ConfigJsonObject private constructor(
    private val values: LinkedHashMap<String, JsonElement>,
) {
    constructor() : this(LinkedHashMap())
    constructor(text: String) : this(ConfigJson.parseElement(text) as? JsonObject
        ?: throw ConfigJsonException("JSON value is not an object"))
    internal constructor(element: JsonObject) : this(LinkedHashMap(element))

    fun getString(key: String): String = requireElement(key).jsonPrimitiveString(key)
    fun optString(key: String): String =
        values[key]?.takeUnless { it is JsonNull }?.optionalStringValue().orEmpty()
    fun getBoolean(key: String): Boolean = requireElement(key).jsonPrimitiveBoolean(key)
    fun optBoolean(key: String): Boolean =
        values[key]?.takeUnless { it is JsonNull }?.tryJsonPrimitiveBoolean(key) ?: false
    fun getInt(key: String): Int = requireElement(key).jsonPrimitiveInt(key)
    fun optInt(key: String): Int =
        values[key]?.takeUnless { it is JsonNull }?.tryJsonPrimitiveInt(key) ?: 0
    fun getLong(key: String): Long = requireElement(key).jsonPrimitiveLong(key)
    fun optLong(key: String): Long =
        values[key]?.takeUnless { it is JsonNull }?.tryJsonPrimitiveLong(key) ?: 0L
    fun getDouble(key: String): Double = requireElement(key).jsonPrimitiveDouble(key)
    fun optDouble(key: String): Double =
        values[key]?.takeUnless { it is JsonNull }?.tryJsonPrimitiveDouble(key) ?: 0.0
    fun has(key: String): Boolean = values.containsKey(key)
    fun isNull(key: String): Boolean = values[key] == null || values[key] is JsonNull
    fun keys(): Iterator<String> = values.keys.iterator()

    fun getJSONObject(key: String): ConfigJsonObject {
        val element = requireElement(key)
        return ConfigJsonObject(element as? JsonObject ?: throw ConfigJsonException("$key is not an object"))
    }

    fun optJSONObject(key: String): ConfigJsonObject? {
        return (values[key] as? JsonObject)?.let(::ConfigJsonObject)
    }

    fun getJSONArray(key: String): ConfigJsonArray {
        val element = requireElement(key)
        return ConfigJsonArray(element as? JsonArray ?: throw ConfigJsonException("$key is not an array"))
    }

    fun optJSONArray(key: String): ConfigJsonArray? {
        return (values[key] as? JsonArray)?.let(::ConfigJsonArray)
    }

    /**
     * Strict access requires the key to exist; an explicit JSON null is returned as Kotlin null.
     * [opt] is the tolerant counterpart and returns null for missing, null, or invalid values.
     */
    fun get(key: String): Any? {
        if (!values.containsKey(key)) {
            throw ConfigJsonException("Missing JSON key: $key")
        }
        return ConfigJson.convert(values[key])
    }

    fun opt(key: String): Any? {
        val element = values[key] ?: return null
        return try {
            ConfigJson.convert(element)
        } catch (_: ConfigJsonException) {
            null
        }
    }

    fun put(key: String, value: Any?): ConfigJsonObject {
        values[key] = ConfigJson.toElement(value)
        return this
    }

    internal fun toElement(): JsonObject = JsonObject(values)
    override fun toString(): String = ConfigJson.toJsonString(toElement())
    fun toString(indentSpaces: Int): String = ConfigJson.toJsonString(toElement(), indentSpaces)

    private fun requireElement(key: String): JsonElement {
        return values[key]?.takeUnless { it is JsonNull } ?: throw ConfigJsonException("Missing JSON key: $key")
    }
}

class ConfigJsonArray private constructor(
    private val values: MutableList<JsonElement>,
) {
    constructor() : this(mutableListOf())
    constructor(items: Collection<Any?>) : this(items.map(ConfigJson::toElement).toMutableList())
    internal constructor(element: JsonArray) : this(element.toMutableList())

    fun length(): Int = values.size

    fun get(index: Int): Any? {
        if (index !in values.indices) {
            throw ConfigJsonException("Missing JSON index: $index")
        }
        return ConfigJson.convert(values[index])
    }

    fun opt(index: Int): Any? {
        val element = values.getOrNull(index) ?: return null
        return try {
            ConfigJson.convert(element)
        } catch (_: ConfigJsonException) {
            null
        }
    }

    fun getString(index: Int): String = requireElement(index).jsonPrimitiveString(index.toString())
    fun getBoolean(index: Int): Boolean = requireElement(index).jsonPrimitiveBoolean(index.toString())
    fun getInt(index: Int): Int = requireElement(index).jsonPrimitiveInt(index.toString())
    fun getLong(index: Int): Long = requireElement(index).jsonPrimitiveLong(index.toString())
    fun getDouble(index: Int): Double = requireElement(index).jsonPrimitiveDouble(index.toString())
    fun optString(index: Int): String =
        values.getOrNull(index)?.takeUnless { it is JsonNull }?.optionalStringValue().orEmpty()
    fun optBoolean(index: Int): Boolean =
        values.getOrNull(index)?.takeUnless { it is JsonNull }?.tryJsonPrimitiveBoolean(index.toString()) ?: false
    fun optInt(index: Int): Int =
        values.getOrNull(index)?.takeUnless { it is JsonNull }?.tryJsonPrimitiveInt(index.toString()) ?: 0
    fun optLong(index: Int): Long =
        values.getOrNull(index)?.takeUnless { it is JsonNull }?.tryJsonPrimitiveLong(index.toString()) ?: 0L
    fun optDouble(index: Int): Double =
        values.getOrNull(index)?.takeUnless { it is JsonNull }?.tryJsonPrimitiveDouble(index.toString()) ?: 0.0

    fun getJSONObject(index: Int): ConfigJsonObject {
        val element = requireElement(index)
        return ConfigJsonObject(element as? JsonObject ?: throw ConfigJsonException("$index is not an object"))
    }

    fun optJSONArray(index: Int): ConfigJsonArray? {
        return (values.getOrNull(index) as? JsonArray)?.let(::ConfigJsonArray)
    }

    fun put(value: Any?): ConfigJsonArray {
        values += ConfigJson.toElement(value)
        return this
    }

    internal fun toElement(): JsonArray = JsonArray(values)
    override fun toString(): String = ConfigJson.toJsonString(toElement())
    fun toString(indentSpaces: Int): String = ConfigJson.toJsonString(toElement(), indentSpaces)

    private fun requireElement(index: Int): JsonElement {
        return values.getOrNull(index)?.takeUnless { it is JsonNull } ?: throw ConfigJsonException("Missing JSON index: $index")
    }
}

private fun JsonElement.jsonPrimitiveString(name: String): String {
    return (this as? JsonPrimitive)?.contentOrNull ?: throw ConfigJsonException("$name is not a string")
}

private fun JsonElement.optionalStringValue(): String {
    return when (this) {
        is JsonPrimitive -> this.content
        else -> toString()
    }
}

private fun JsonElement.jsonPrimitiveBoolean(name: String): Boolean {
    return (this as? JsonPrimitive)?.booleanOrNull ?: throw ConfigJsonException("$name is not a boolean")
}

private fun JsonElement.tryJsonPrimitiveBoolean(name: String): Boolean? {
    return try {
        jsonPrimitiveBoolean(name)
    } catch (_: ConfigJsonException) {
        null
    }
}

private fun JsonElement.jsonPrimitiveInt(name: String): Int {
    return (this as? JsonPrimitive)?.intOrNull ?: throw ConfigJsonException("$name is not an int")
}

private fun JsonElement.tryJsonPrimitiveInt(name: String): Int? {
    return try {
        jsonPrimitiveInt(name)
    } catch (_: ConfigJsonException) {
        null
    }
}

private fun JsonElement.jsonPrimitiveLong(name: String): Long {
    return (this as? JsonPrimitive)?.longOrNull ?: throw ConfigJsonException("$name is not a long")
}

private fun JsonElement.tryJsonPrimitiveLong(name: String): Long? {
    return try {
        jsonPrimitiveLong(name)
    } catch (_: ConfigJsonException) {
        null
    }
}

private fun JsonElement.jsonPrimitiveDouble(name: String): Double {
    val value = (this as? JsonPrimitive)?.doubleOrNull
    return value?.takeIf { it.isFinite() } ?: throw ConfigJsonException("$name is not a finite double")
}

private fun JsonElement.tryJsonPrimitiveDouble(name: String): Double? {
    return try {
        jsonPrimitiveDouble(name)
    } catch (_: ConfigJsonException) {
        null
    }
}
