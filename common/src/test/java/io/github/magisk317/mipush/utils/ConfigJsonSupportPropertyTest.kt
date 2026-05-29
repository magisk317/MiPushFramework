package io.github.magisk317.mipush.utils

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.RepeatedTest
import kotlin.random.Random

/**
 * Property-based tests for ConfigJsonSupport.
 *
 * **Validates: Requirements 9.1**
 *
 * Uses JUnit 5 with manual random input generation (consistent with batch 1 pattern).
 */
class ConfigJsonSupportPropertyTest {

    /**
     * Property 2: ConfigJsonSupport valid JSON validation —
     * for random valid JSON strings, `validateAndFormat` returns `valid = true`
     * and `formatted` non-null.
     *
     * **Validates: Requirements 9.1**
     */
    @RepeatedTest(100)
    fun `validateAndFormat returns valid true and formatted non-null for valid JSON`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val json = generateRandomValidJson(rng)

        val result = ConfigJsonSupport.validateAndFormat(json)

        assertTrue(
            result.valid,
            "Expected valid=true for generated JSON, but got valid=false. " +
                "Input='$json', seed=$seed, errorMessage='${result.errorMessage}'"
        )
        assertNotNull(
            result.formatted,
            "Expected formatted non-null for valid JSON. Input='$json', seed=$seed"
        )
    }

    /**
     * Property 2 (extended): validateAndFormat returns valid=true for various
     * JSON structures (objects, arrays, nested).
     *
     * **Validates: Requirements 9.1**
     */
    @RepeatedTest(50)
    fun `validateAndFormat handles diverse valid JSON structures`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val jsonVariants = listOf(
            generateRandomJsonObject(rng),
            generateRandomJsonArray(rng),
            generateNestedJsonObject(rng),
        )

        for (json in jsonVariants) {
            val result = ConfigJsonSupport.validateAndFormat(json)
            assertTrue(
                result.valid,
                "Expected valid=true for JSON='$json', seed=$seed, error='${result.errorMessage}'"
            )
            assertNotNull(
                result.formatted,
                "Expected formatted non-null for JSON='$json', seed=$seed"
            )
        }
    }

    /**
     * Property 3: ConfigJsonSupport stableSha determinism —
     * for random input strings, `stableSha` produces deterministic 64-character
     * lowercase hex.
     *
     * **Validates: Requirements 9.1**
     */
    @RepeatedTest(100)
    fun `stableSha produces deterministic 64-char lowercase hex for any input`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val input = generateRandomString(rng)

        val sha1 = ConfigJsonSupport.stableSha(input)
        val sha2 = ConfigJsonSupport.stableSha(input)

        // Deterministic: same input always produces same output
        assertEquals(
            sha1,
            sha2,
            "stableSha is not deterministic for input='$input', seed=$seed"
        )

        // 64 characters (SHA-256 hex)
        assertEquals(
            64,
            sha1.length,
            "stableSha should produce 64-char hex, got ${sha1.length} chars for input='$input', seed=$seed"
        )

        // Lowercase hex only
        assertTrue(
            sha1.all { it in '0'..'9' || it in 'a'..'f' },
            "stableSha should be lowercase hex, got '$sha1' for input='$input', seed=$seed"
        )
    }

    /**
     * Property 3 (extended): stableSha determinism holds across multiple calls
     * with the same input.
     *
     * **Validates: Requirements 9.1**
     */
    @RepeatedTest(50)
    fun `stableSha determinism holds across multiple invocations`() {
        val seed = Random.nextLong()
        val rng = Random(seed)

        val input = generateRandomString(rng)

        val results = (1..5).map { ConfigJsonSupport.stableSha(input) }

        // All results must be identical
        for (i in 1 until results.size) {
            assertEquals(
                results[0],
                results[i],
                "stableSha not deterministic on call ${i + 1} for input='$input', seed=$seed"
            )
        }
    }

    // --- Generators ---

    /**
     * Generates a random valid JSON string (object or array).
     */
    private fun generateRandomValidJson(rng: Random): String {
        return if (rng.nextBoolean()) {
            generateRandomJsonObject(rng)
        } else {
            generateRandomJsonArray(rng)
        }
    }

    /**
     * Generates a random JSON object with string key-value pairs.
     */
    private fun generateRandomJsonObject(rng: Random): String {
        val numEntries = rng.nextInt(1, 6)
        val entries = (1..numEntries).joinToString(",") { i ->
            val key = generateSafeJsonString(rng)
            val value = generateRandomJsonValue(rng, depth = 0)
            "\"$key\":$value"
        }
        return "{$entries}"
    }

    /**
     * Generates a random JSON array.
     */
    private fun generateRandomJsonArray(rng: Random): String {
        val numElements = rng.nextInt(1, 6)
        val elements = (1..numElements).joinToString(",") {
            generateRandomJsonValue(rng, depth = 0)
        }
        return "[$elements]"
    }

    /**
     * Generates a nested JSON object (depth 2).
     */
    private fun generateNestedJsonObject(rng: Random): String {
        val key1 = generateSafeJsonString(rng)
        val key2 = generateSafeJsonString(rng)
        val innerObj = generateRandomJsonObject(rng)
        val innerArr = generateRandomJsonArray(rng)
        return "{\"$key1\":$innerObj,\"$key2\":$innerArr}"
    }

    /**
     * Generates a random JSON value (string, number, boolean, null, or nested at depth 0).
     */
    private fun generateRandomJsonValue(rng: Random, depth: Int): String {
        val type = if (depth >= 2) rng.nextInt(4) else rng.nextInt(6)
        return when (type) {
            0 -> "\"${generateSafeJsonString(rng)}\""
            1 -> rng.nextInt(-1000, 1000).toString()
            2 -> rng.nextBoolean().toString()
            3 -> "null"
            4 -> {
                val n = rng.nextInt(1, 3)
                val entries = (1..n).joinToString(",") {
                    "\"${generateSafeJsonString(rng)}\":${generateRandomJsonValue(rng, depth + 1)}"
                }
                "{$entries}"
            }
            5 -> {
                val n = rng.nextInt(1, 3)
                val elements = (1..n).joinToString(",") {
                    generateRandomJsonValue(rng, depth + 1)
                }
                "[$elements]"
            }
            else -> "null"
        }
    }

    /**
     * Generates a safe string for use as JSON key/value (alphanumeric only to avoid escaping issues).
     */
    private fun generateSafeJsonString(rng: Random): String {
        val len = rng.nextInt(1, 10)
        return (1..len).map {
            val chars = "abcdefghijklmnopqrstuvwxyz0123456789"
            chars[rng.nextInt(chars.length)]
        }.joinToString("")
    }

    /**
     * Generates a random string of varying content (may or may not be valid JSON).
     */
    private fun generateRandomString(rng: Random): String {
        val len = rng.nextInt(0, 200)
        return (1..len).map {
            // Mix of printable ASCII characters
            (32 + rng.nextInt(95)).toChar()
        }.joinToString("")
    }
}
