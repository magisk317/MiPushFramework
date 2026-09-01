package io.github.magisk317.mipush.common.configurations

/** Neutral field result exposed by a platform-specific field accessor. */
data class ConfigFieldValue(
    val value: Any?,
    val isContainer: Boolean = false,
)

fun interface ConfigFieldAccessor {
    fun read(path: List<String>): ConfigFieldValue
}

/** Pure configuration match and placeholder replacement decisions. */
object ConfigRulePlans {
    fun match(
        config: ConfigJsonObject?,
        accessor: ConfigFieldAccessor,
    ): Map<String, String>? {
        if (config == null) return emptyMap()
        val groups = linkedMapOf<String, String>()
        return if (matchObject(config, emptyList(), accessor, groups)) groups else null
    }

    fun replacePlaceholders(
        value: String,
        groups: Map<String, String>?,
    ): String {
        val pattern = Regex("\\$\\$|\\$\\{([^}]+)\\}")
        return pattern.replace(value) { match ->
            if (match.value == "$$") {
                "$"
            } else {
                val name = match.groups[1]?.value
                groups?.get(name) ?: match.value
            }
        }
    }

    private fun matchObject(
        config: ConfigJsonObject,
        path: List<String>,
        accessor: ConfigFieldAccessor,
        groups: MutableMap<String, String>,
    ): Boolean {
        val keys = config.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val nextPath = path + key
            val actual = accessor.read(nextPath)
            if (config.isNull(key)) {
                if (actual.value != null) return false
                continue
            }
            if (actual.value == null) return false

            val nested = config.optJSONObject(key)
            if (nested != null) {
                if (!actual.isContainer || !matchObject(nested, nextPath, accessor, groups)) {
                    return false
                }
            } else if (!matches(config.optString(key), actual.value.toString(), groups)) {
                return false
            }
        }
        return true
    }

    private fun matches(
        expression: String,
        value: String,
        groups: MutableMap<String, String>,
    ): Boolean {
        val result = Regex(expression).find(value) ?: return false
        for (name in namedGroupNames(expression)) {
            groups[name] = result.groups[name]?.value ?: ""
        }
        return true
    }

    private fun namedGroupNames(expression: String): List<String> {
        val names = mutableListOf<String>()
        var index = 0
        while (index + 3 < expression.length) {
            if (
                expression[index] == '(' &&
                expression[index + 1] == '?' &&
                expression[index + 2] == '<' &&
                (index == 0 || expression[index - 1] != '\\')
            ) {
                val end = expression.indexOf('>', index + 3)
                if (end > index + 3) {
                    val name = expression.substring(index + 3, end)
                    if (name.first().isLetter() && name.drop(1).all { it.isLetterOrDigit() }) {
                        names += name
                    }
                    index = end
                }
            }
            index++
        }
        return names
    }
}
