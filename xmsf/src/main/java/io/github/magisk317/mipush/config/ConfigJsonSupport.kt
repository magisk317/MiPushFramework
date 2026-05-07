package io.github.magisk317.mipush.config

import java.security.MessageDigest
import java.util.regex.Pattern
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject

object ConfigJsonSupport {
    private val POSITION_PATTERN = Pattern.compile("offset (\\d+)")
    private const val UTF8_BOM = '\uFEFF'
    private val prettyJson = Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }
    private val compactJson = Json {
        prettyPrint = false
        ignoreUnknownKeys = true
    }

    fun validateAndFormat(rawText: String): JsonValidationResult {
        return try {
            val formatted = formatJson(rawText)
            JsonValidationResult(valid = true, formatted = formatted)
        } catch (error: SerializationException) {
            val message = buildValidationMessage(stripBom(rawText), error)
            JsonValidationResult(
                valid = false,
                formatted = null,
                errorMessage = message.first,
                line = message.second,
                column = message.third,
            )
        }
    }

    fun formatOrOriginal(rawText: String): String {
        return validateAndFormat(rawText).formatted ?: rawText
    }

    fun stableSha(rawText: String): String {
        val canonical = runCatching { canonicalJson(rawText) }.getOrElse { stripBom(rawText) }
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(canonical.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun formatJson(rawText: String): String {
        val element = compactJson.parseToJsonElement(stripBom(rawText).trim())
        return prettyJson.encodeToString(JsonElement.serializer(), element)
    }

    private fun canonicalJson(rawText: String): String {
        val element = compactJson.parseToJsonElement(stripBom(rawText).trim())
        return compactJson.encodeToString(JsonElement.serializer(), sortElement(element))
    }

    private fun buildValidationMessage(rawText: String, error: SerializationException): Triple<String, Int?, Int?> {
        val original = error.toString()
        val matcher = POSITION_PATTERN.matcher(original)
        if (!matcher.find()) {
            return Triple(
                original.removePrefix("kotlinx.serialization.json.internal.JsonDecodingException: "),
                null,
                null,
            )
        }
        val pos = matcher.group(1)?.toIntOrNull() ?: return Triple(original, null, null)
        val safePos = pos.coerceIn(0, rawText.length)
        val lines = rawText.substring(0, safePos).split('\n')
        val line = lines.size.coerceAtLeast(1)
        val column = (lines.lastOrNull()?.length ?: 0) + 1
        val shortMessage = original.substring(0, matcher.start())
            .removePrefix("kotlinx.serialization.json.internal.JsonDecodingException: ")
            .trim()
        return Triple("$shortMessage line $line column $column", line, column)
    }

    private fun stripBom(rawText: String): String {
        return rawText.removePrefix(UTF8_BOM.toString())
    }

    private fun sortElement(element: JsonElement): JsonElement {
        return when (element) {
            is JsonObject -> JsonObject(
                element.entries
                    .sortedBy { it.key }
                    .associate { (key, value) -> key to sortElement(value) },
            )

            is JsonArray -> JsonArray(element.map(::sortElement))
            else -> element
        }
    }
}
