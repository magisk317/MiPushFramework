package io.github.magisk317.mipush.common.configurations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class LispEvaluatorTest {
    @Test
    fun `cond evaluates matched clause and nested expression`() {
        val expression = ConfigJsonArray(
            listOf(
                "cond",
                ConfigJsonArray(
                    listOf(
                        ConfigJsonArray(listOf("matches")),
                        ConfigJsonArray(listOf("replace", "hello", "hello", "world")),
                    ),
                ),
                ConfigJsonArray(listOf(ConfigJsonArray(listOf("matches")), "fallback")),
            ),
        )

        val result = LispEvaluator.evaluate(expression, LispEvaluator.Extension { value ->
            if (value is ConfigJsonArray && value.optString(0) == "matches") true else value
        })

        assertEquals("world", result)
    }

    @Test
    fun `property and json parsing remain platform neutral`() {
        val objectValue = ConfigJsonObject().put("name", "MiPush")
        val property = ConfigJsonArray(listOf("property", "name", objectValue))
        val parse = ConfigJsonArray(listOf("parse-json", "{\"enabled\":true}"))

        val passthrough = LispEvaluator.Extension { value ->
            value.takeIf { it is ConfigJsonObject }
        }
        assertEquals("MiPush", LispEvaluator.evaluate(property, passthrough))
        assertEquals(true, (LispEvaluator.evaluate(parse, LispEvaluator.Extension { null }) as ConfigJsonObject)
            .getBoolean("enabled"))
    }

    @Test
    fun `platform codec is optional and extension handles unknown methods`() {
        val decode = ConfigJsonArray(listOf("decode-uri", "hello%20world"))
        val unknown = ConfigJsonArray(listOf("custom", "value"))

        assertNull(LispEvaluator.evaluate(decode, LispEvaluator.Extension { null }))
        assertEquals("handled", LispEvaluator.evaluate(unknown, LispEvaluator.Extension { "handled" }))
    }
}
