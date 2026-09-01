package io.github.magisk317.mipush.common.configurations

/** Platform-neutral evaluator for configuration expressions. */
object LispEvaluator {
    fun interface Extension {
        fun evaluate(expr: Any?): Any?
    }

    /** Optional platform codec for operations that cannot be implemented in commonMain. */
    interface Codec {
        fun decodeUri(value: String): String?

        fun decodeBase64(value: String): String?
    }

    fun evaluate(expr: Any?, extension: Extension, codec: Codec? = null): Any? {
        if (expr is String) return expr
        if (expr is ConfigJsonArray) return evaluateArray(expr, extension, codec)
        return extension.evaluate(expr)
    }

    private fun evaluateArray(
        expr: ConfigJsonArray,
        extension: Extension,
        codec: Codec?,
    ): Any? {
        val method = evaluate(expr.opt(0), extension, codec) as? String ?: return null
        if (method == "cond") return evaluateCond(expr, extension, codec)

        val evaluated = ConfigJsonArray().apply {
            put(method)
            for (index in 1 until expr.length()) {
                put(evaluate(expr.opt(index), extension, codec))
            }
        }

        return try {
            when (method) {
                "hash" -> evaluated.optString(1).hashCode()
                "decode-uri" -> codec?.decodeUri(evaluated.optString(1))
                "decode-base64" -> codec?.decodeBase64(evaluated.optString(1))
                "parse-json" -> ConfigJson.parse(evaluated.optString(1))
                "property" -> property(evaluated)
                "replace" -> {
                    val source = evaluated.optString(1)
                    val pattern = evaluated.optString(2)
                    val replacement = evaluated.optString(3)
                    source.replace(pattern.toRegex(), replacement)
                }
                else -> extension.evaluate(evaluated)
            }
        } catch (_: IllegalArgumentException) {
            null
        } catch (_: ConfigJsonException) {
            null
        }
    }

    private fun property(expr: ConfigJsonArray): Any? {
        val value = expr.opt(2)
        return when (value) {
            is ConfigJsonObject -> value.opt(expr.optString(1))
            is ConfigJsonArray -> value.opt(expr.optInt(1))
            else -> null
        }
    }

    private fun evaluateCond(
        expr: ConfigJsonArray,
        extension: Extension,
        codec: Codec?,
    ): Any? {
        for (index in 1 until expr.length()) {
            val clause = expr.optJSONArray(index) ?: return null
            val test = clause.opt(0)
            if (test is ConfigJsonArray && evaluateArray(test, extension, codec) == true) {
                var result: Any? = null
                for (clauseIndex in 1 until clause.length()) {
                    result = evaluate(clause.opt(clauseIndex), extension, codec)
                }
                return result
            }
            val subCondition = ConfigJsonArray().apply {
                put("cond")
                put(clause)
            }
            val value = extension.evaluate(subCondition)
            if (value != null) return value
        }
        return null
    }
}
