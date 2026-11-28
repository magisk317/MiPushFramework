package io.github.magisk317.mipush.hook.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NativeFocusPromotionRegistryTest {
    @Test
    fun `issued token is bound to package and uid and consumed once`() {
        var now = 1_000L
        val registry = NativeFocusPromotionRegistry(
            nowMillis = { now },
            tokenFactory = tokenSequence("a".repeat(32)),
        )
        val token = requireNotNull(registry.issue("com.example.target", 10_123))

        assertFalse(registry.consume(token, "com.other.app", 10_123))
        assertFalse(registry.consume(token, "com.example.target", 10_123))
    }

    @Test
    fun `uid mismatch also burns token`() {
        val registry = NativeFocusPromotionRegistry(
            nowMillis = { 1_000L },
            tokenFactory = tokenSequence("4".repeat(32)),
        )
        val token = requireNotNull(registry.issue("com.example.target", 10_123))

        assertFalse(registry.consume(token, "com.example.target", 10_124))
        assertFalse(registry.consume(token, "com.example.target", 10_123))
    }

    @Test
    fun `matching identity consumes token once`() {
        val registry = NativeFocusPromotionRegistry(
            nowMillis = { 1_000L },
            tokenFactory = tokenSequence("b".repeat(32)),
        )
        val token = requireNotNull(registry.issue("com.example.target", 10_123))

        assertTrue(registry.consume(token, "com.example.target", 10_123))
        assertFalse(registry.consume(token, "com.example.target", 10_123))
    }

    @Test
    fun `unknown and forged tokens are rejected`() {
        val registry = NativeFocusPromotionRegistry(
            nowMillis = { 1_000L },
            tokenFactory = tokenSequence("c".repeat(32)),
        )
        val issued = requireNotNull(registry.issue("com.example.target", 10_123))

        assertFalse(registry.consume("d".repeat(32), "com.example.target", 10_123))
        assertNotEquals(issued, "d".repeat(32))
        assertTrue(registry.consume(issued, "com.example.target", 10_123))
    }

    @Test
    fun `expired token is rejected and releases bounded capacity`() {
        var now = 1_000L
        val tokens = tokenSequence("e".repeat(32), "f".repeat(32))
        val registry = NativeFocusPromotionRegistry(
            maxEntries = 1,
            tokenLifetimeMs = 50,
            nowMillis = { now },
            tokenFactory = tokens,
        )
        val expired = requireNotNull(registry.issue("com.example.first", 10_123))
        assertNull(registry.issue("com.example.second", 10_124))

        now = 1_050L
        assertFalse(registry.consume(expired, "com.example.first", 10_123))
        val replacement = requireNotNull(registry.issue("com.example.second", 10_124))
        assertTrue(registry.consume(replacement, "com.example.second", 10_124))
    }

    @Test
    fun `capacity rejects new token without evicting live token`() {
        val tokens = tokenSequence("1".repeat(32), "2".repeat(32))
        val registry = NativeFocusPromotionRegistry(
            maxEntries = 1,
            nowMillis = { 1_000L },
            tokenFactory = tokens,
        )
        val first = requireNotNull(registry.issue("com.example.first", 10_123))

        assertNull(registry.issue("com.example.second", 10_124))
        assertTrue(registry.consume(first, "com.example.first", 10_123))
        assertEquals("2".repeat(32), registry.issue("com.example.second", 10_124))
    }

    @Test
    fun `token collisions never replace an existing authorization`() {
        val repeated = "5".repeat(32)
        val tokens = tokenSequence(repeated, repeated, "6".repeat(32))
        val registry = NativeFocusPromotionRegistry(
            nowMillis = { 1_000L },
            tokenFactory = tokens,
        )
        val first = requireNotNull(registry.issue("com.example.first", 10_123))
        val second = requireNotNull(registry.issue("com.example.second", 10_124))

        assertEquals(repeated, first)
        assertEquals("6".repeat(32), second)
        assertTrue(registry.consume(first, "com.example.first", 10_123))
        assertTrue(registry.consume(second, "com.example.second", 10_124))
        assertFalse(registry.consume(first, "com.example.first", 10_123))
    }

    @Test
    fun `expiry saturates instead of overflowing`() {
        var now = Long.MAX_VALUE - 5
        val registry = NativeFocusPromotionRegistry(
            tokenLifetimeMs = 10,
            nowMillis = { now },
            tokenFactory = tokenSequence("7".repeat(32)),
        )
        val token = requireNotNull(registry.issue("com.example.target", 10_123))

        assertTrue(registry.consume(token, "com.example.target", 10_123))
        now = Long.MAX_VALUE
        assertFalse(registry.consume(token, "com.example.target", 10_123))
    }

    @Test
    fun `invalid identities and blank tokens are rejected`() {
        val registry = NativeFocusPromotionRegistry(
            nowMillis = { 1_000L },
            tokenFactory = tokenSequence("3".repeat(32)),
        )

        assertNull(registry.issue("", 10_123))
        assertNull(registry.issue("com.example.target", -1))
        assertFalse(registry.consume(null, "com.example.target", 10_123))
        assertFalse(registry.consume("", "com.example.target", 10_123))
    }

    private fun tokenSequence(vararg tokens: String): () -> String {
        val values = tokens.iterator()
        return { if (values.hasNext()) values.next() else error("test token sequence exhausted") }
    }
}
