package io.github.magisk317.mipush.diagnostics

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class StructuredLogCoreTest {
    @Test fun `encoding is deterministic and redacts credentials and addresses`() {
        val line = StructuredLogCore.encode(mapOf("z" to "token=abc", "a" to "host=192.0.2.1"))
        assertTrue(line.indexOf("\"a\"") < line.indexOf("\"z\""))
        assertTrue(line.contains("token=redacted"))
        assertTrue(line.contains("redacted-ip"))
    }
    @Test fun `null fields remain json null`() {
        assertEquals("{\"value\":null}", StructuredLogCore.encode(mapOf("value" to null)))
    }

    @Test fun `json strings escape every control character`() {
        val line = StructuredLogCore.encode(
            StructuredLogCore.stringField("value", "quote\" slash\\ newline\n tab\t control\u0001"),
        )
        assertEquals(
            "{\"value\":\"quote\\\" slash\\\\ newline\\n tab\\t control\\u0001\"}",
            line,
        )
    }

    @Test fun `number fields reject non finite values`() {
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException::class.java) {
            StructuredLogCore.numberField("value", Double.NaN)
        }
    }
}
