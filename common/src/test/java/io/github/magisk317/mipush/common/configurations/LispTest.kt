package io.github.magisk317.mipush.common.configurations

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class LispTest {
    @Test
    fun `cond evaluates expressions from matched clause`() {
        val expr = ConfigJsonArray(
            listOf(
                "cond",
                ConfigJsonArray(listOf(ConfigJsonArray(listOf("matches")), "first", "expected")),
                ConfigJsonArray(listOf(ConfigJsonArray(listOf("matches")), "wrong")),
            )
        )

        val result = Lisp.evaluate(expr) { evaluated ->
            if (evaluated is ConfigJsonArray && evaluated.optString(0) == "matches") {
                true
            } else {
                evaluated
            }
        }

        assertEquals("expected", result)
    }
}
