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
            is JsonPrimitive -> element.booleanOrNull
                ?: element.longOrNull
                ?: element.doubleOrNull
                ?: element.contentOrNull
            else -> element.toString()
        }
    }

    internal fun toElement(value: Any?): JsonElement {
        return when (value) {
            null -> JsonNull
            is ConfigJsonObject -> value.toElement()
            is ConfigJsonArray -> value.toElement()
            is JsonElement -> value
            is Boolean -> JsonPrimitive(value)
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
    fun optString(key: String): String = values[key]?.takeUnless { it is JsonNull }?.jsonPrimitiveString(key).orEmpty()
    fun getBoolean(key: String): Boolean = requireElement(key).jsonPrimitiveBoolean(key)
    fun getInt(key: String): Int = requireElement(key).jsonPrimitiveInt(key)
    fun optInt(key: String): Int = values[key]?.takeUnless { it is JsonNull }?.jsonPrimitiveInt(key) ?: 0
    fun getLong(key: String): Long = requireElement(key).jsonPrimitiveLong(key)
    fun getDouble(key: String): Double = requireElement(key).jsonPrimitiveDouble(key)
    fun has(key: String): Boolean = values.containsKey(key)
    fun isNull(key: String): Boolean = values[key] == null || values[key] is JsonNull
    fun keys(): Iterator<String> = values.keys.iterator()

    fun getJSONObject(key: String): ConfigJsonObject {
        val element = requireElement(key)
        return ConfigJsonObject(element as? JsonObject ?: throw ConfigJsonException("$key is not an object"))
    }

    fun getConfigJsonObject(key: String): ConfigJsonObject = getJSONObject(key)

    fun optJSONObject(key: String): ConfigJsonObject? {
        return (values[key] as? JsonObject)?.let(::ConfigJsonObject)
    }

    fun optConfigJsonObject(key: String): ConfigJsonObject? = optJSONObject(key)

    fun getJSONArray(key: String): ConfigJsonArray {
        val element = requireElement(key)
        return ConfigJsonArray(element as? JsonArray ?: throw ConfigJsonException("$key is not an array"))
    }

    fun getConfigJsonArray(key: String): ConfigJsonArray = getJSONArray(key)

    fun optJSONArray(key: String): ConfigJsonArray? {
        return (values[key] as? JsonArray)?.let(::ConfigJsonArray)
    }

    fun optConfigJsonArray(key: String): ConfigJsonArray? = optJSONArray(key)

    fun get(key: String): Any? = ConfigJson.convert(values[key])
    fun opt(key: String): Any? = get(key)

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
    fun get(index: Int): Any? = ConfigJson.convert(values.getOrNull(index))
    fun opt(index: Int): Any? = get(index)
    fun getString(index: Int): String = requireElement(index).jsonPrimitiveString(index.toString())
    fun getInt(index: Int): Int = requireElement(index).jsonPrimitiveInt(index.toString())
    fun optString(index: Int): String = values.getOrNull(index)?.takeUnless { it is JsonNull }?.jsonPrimitiveString(index.toString()).orEmpty()
    fun optInt(index: Int): Int = values.getOrNull(index)?.takeUnless { it is JsonNull }?.jsonPrimitiveInt(index.toString()) ?: 0

    fun getJSONObject(index: Int): ConfigJsonObject {
        val element = requireElement(index)
        return ConfigJsonObject(element as? JsonObject ?: throw ConfigJsonException("$index is not an object"))
    }

    fun getConfigJsonObject(index: Int): ConfigJsonObject = getJSONObject(index)

    fun optJSONArray(index: Int): ConfigJsonArray? {
        return (values.getOrNull(index) as? JsonArray)?.let(::ConfigJsonArray)
    }

    fun optConfigJsonArray(index: Int): ConfigJsonArray? = optJSONArray(index)

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

private fun JsonElement.jsonPrimitiveBoolean(name: String): Boolean {
    return (this as? JsonPrimitive)?.booleanOrNull ?: throw ConfigJsonException("$name is not a boolean")
}

private fun JsonElement.jsonPrimitiveInt(name: String): Int {
    return (this as? JsonPrimitive)?.intOrNull ?: throw ConfigJsonException("$name is not an int")
}

private fun JsonElement.jsonPrimitiveLong(name: String): Long {
    return (this as? JsonPrimitive)?.longOrNull ?: throw ConfigJsonException("$name is not a long")
}

private fun JsonElement.jsonPrimitiveDouble(name: String): Double {
    return (this as? JsonPrimitive)?.doubleOrNull ?: throw ConfigJsonException("$name is not a double")
}
