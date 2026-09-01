package io.github.magisk317.mipush.diagnostics

/** JSONL field value used by the platform-neutral logging policy. */
sealed interface StructuredLogValue {
    data class StringValue(val value: String) : StructuredLogValue
    data class NumberValue(val encoded: String) : StructuredLogValue
    data object NullValue : StructuredLogValue
}

data class StructuredLogField(
    val name: String,
    val value: StructuredLogValue,
    val include: Boolean = true,
)

/** Deterministic JSONL encoding and redaction policy; file I/O remains in platform adapters. */
object StructuredLogCore {
    fun stringField(name: String, value: String, include: Boolean = true) =
        StructuredLogField(name, StructuredLogValue.StringValue(value), include)

    fun nullableStringField(name: String, value: String?, include: Boolean = true) =
        StructuredLogField(
            name = name,
            value = value?.let(StructuredLogValue::StringValue) ?: StructuredLogValue.NullValue,
            include = include,
        )

    fun numberField(name: String, value: Number, include: Boolean = true): StructuredLogField {
        val encoded = value.toString()
        require(JSON_NUMBER_PATTERN.matches(encoded)) {
            "Invalid JSON number for field '$name': $encoded"
        }
        return StructuredLogField(name, StructuredLogValue.NumberValue(encoded), include)
    }

    fun encode(fields: Iterable<StructuredLogField>): String = buildString {
        append('{')
        var first = true
        fields.forEach { field ->
            if (!field.include) return@forEach
            if (!first) append(',')
            first = false
            appendJsonString(field.name)
            append(':')
            when (val value = field.value) {
                is StructuredLogValue.StringValue -> appendJsonString(redact(value.value))
                is StructuredLogValue.NumberValue -> append(value.encoded)
                StructuredLogValue.NullValue -> append("null")
            }
        }
        append('}')
    }

    fun encode(vararg fields: StructuredLogField): String = encode(fields.asList())

    fun encode(fields: Map<String, String?>): String = encode(
        fields.entries
            .sortedBy { it.key }
            .map { (name, value) ->
                StructuredLogField(
                    name = name,
                    value = value?.let(StructuredLogValue::StringValue) ?: StructuredLogValue.NullValue,
                )
            },
    )

    fun redact(value: String): String = value
        .replace(SECRET_PATTERN, "$1=redacted")
        .replace(IP_PATTERN, "redacted-ip")

    private fun StringBuilder.appendJsonString(value: String) {
        append('"')
        value.forEach { character ->
            when (character) {
                '\\' -> append("\\\\")
                '"' -> append("\\\"")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) {
                    append("\\u")
                    append(character.code.toString(16).padStart(4, '0'))
                } else {
                    append(character)
                }
            }
        }
        append('"')
    }

    private val SECRET_PATTERN = Regex("(?i)(token|password|secret|authKey)=([^\\s,]+)")
    private val IP_PATTERN = Regex("(?<!\\d)(?:\\d{1,3}\\.){3}\\d{1,3}(?!\\d)")
    private val JSON_NUMBER_PATTERN = Regex("-?(?:0|[1-9]\\d*)(?:\\.\\d+)?(?:[eE][+-]?\\d+)?")
}
