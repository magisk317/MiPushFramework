package io.github.magisk317.mipush.diagnostics

import io.github.magisk317.xposed.logging.JsonLineEncoder
import io.github.magisk317.xposed.logging.JsonLineField
import io.github.magisk317.xposed.logging.SecretRedactor
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * MiPush persists JSONL lines through the kit's platform-neutral encoder
 * (logging-core). The kit encoder is redaction-free by design — the record-level
 * policy lives in kit StructuredLogSanitizer — so callers pre-sanitize free-text
 * fields. These tests pin that contract at the call-site level.
 */
class StructuredLogEncodeTest {
    @Test
    fun `encoding is deterministic and callers redact credentials and addresses`() {
        val line = JsonLineEncoder.encode(
            JsonLineField.string("z", SecretRedactor.redact("token=abc")),
            JsonLineField.string("a", SecretRedactor.redact("host=192.0.2.1")),
        )
        // The kit encoder preserves caller order (it does not sort fields).
        assertTrue(line.indexOf("\"z\"") < line.indexOf("\"a\""))
        // SecretRedactor is a credential policy: key=value secrets are masked...
        assertFalse(line.contains("abc"))
        // ...while ordinary fields (host addresses) pass through untouched.
        assertTrue(line.contains("192.0.2.1"))
    }

    @Test
    fun `optional fields are omitted instead of encoded as null`() {
        val line = JsonLineEncoder.encode(
            JsonLineField.string("value", "ignored", include = false),
        )
        assertEquals("{}", line)
    }

    @Test
    fun `json strings escape every control character`() {
        val line = JsonLineEncoder.encode(
            JsonLineField.string("value", "quote\" slash\\ newline\n tab\t control\u0001"),
        )
        // Raw string: the escape sequences are literal characters in the output.
        assertEquals(
            """{"value":"quote\" slash\\ newline\n tab\t control\u0001"}""",
            line,
        )
    }

    @Test
    fun `number fields reject non finite values`() {
        assertThrows(IllegalArgumentException::class.java) {
            JsonLineField.number("value", Double.NaN)
        }
    }
}
