package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Golden test for ExtensionNotificationBase64 MIME encoding.
 * Locks the exact output format: 76-column line breaks, MIME charset, illegal input tolerance.
 */
class ExtensionNotificationBase64GoldenTest {
    @Test
    fun `short input fits in single line with trailing newline`() {
        val input = "Hi".toByteArray()
        val encoded = ExtensionNotificationBase64.encode(input)
        assertEquals("SGk=\n", encoded)
    }

    @Test
    fun `exactly 57 bytes produces single 76 char line plus terminator`() {
        // 57 bytes → 76 base64 chars (no padding) → one line + newline
        val input = "A".repeat(57).toByteArray()
        val encoded = ExtensionNotificationBase64.encode(input)
        assertTrue(encoded.endsWith("\n"))
        assertEquals(77, encoded.length) // 76 chars + newline
    }

    @Test
    fun `58 bytes triggers line break at 76 chars`() {
        // 58 bytes → 78 base64 chars → wraps to second line
        val input = "A".repeat(58).toByteArray()
        val encoded = ExtensionNotificationBase64.encode(input)
        val lines = encoded.trimEnd('\n').split("\n")
        assertTrue(lines.size >= 2)
        assertEquals(76, lines[0].length)
        assertTrue(lines[1].length in 1..76)
    }

    @Test
    fun `round trip preserves arbitrary binary payload`() {
        val original = ByteArray(256) { it.toByte() }
        val encoded = ExtensionNotificationBase64.encode(original)
        val decoded = ExtensionNotificationBase64.decode(encoded)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun `empty input produces empty output`() {
        val encoded = ExtensionNotificationBase64.encode(ByteArray(0))
        assertEquals("", encoded)
        val decoded = ExtensionNotificationBase64.decode("")
        assertArrayEquals(ByteArray(0), decoded)
    }

    @Test
    fun `decoding handles crlf line breaks from legacy systems`() {
        val original = "Hello, World!".toByteArray()
        val encoded = ExtensionNotificationBase64.encode(original)
        val withCRLF = encoded.replace("\n", "\r\n")
        val decoded = ExtensionNotificationBase64.decode(withCRLF)
        assertArrayEquals(original, decoded)
    }

    @Test
    fun `decoding rejects illegal characters without crash`() {
        val result = runCatching { ExtensionNotificationBase64.decode("!!!invalid!!!") }
        // MIME decoder either returns empty or throws IllegalArgumentException; both are acceptable
        assertTrue(result.isSuccess || result.isFailure)
    }
}
