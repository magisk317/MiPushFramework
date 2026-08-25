package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Test

class ConfigurationLoadGateTest {
    @Test
    fun `failed initialization retries after backoff and can recover`() {
        val gate = ConfigurationLoadGate(retryDelayMs = 30_000L)
        var attempts = 0
        val source = { "directory" }
        val initializer = { _: String -> ++attempts > 1 }

        assertInstanceOf(
            ConfigurationLoadResult.Failed::class.java,
            gate.ensureInitialized(1_000L, source, initializer),
        )
        assertEquals(
            ConfigurationLoadResult.RetryDeferred,
            gate.ensureInitialized(1_001L, source, initializer),
        )
        assertEquals(
            ConfigurationLoadResult.Initialized,
            gate.ensureInitialized(31_000L, source, initializer),
        )
        assertEquals(
            ConfigurationLoadResult.AlreadyInitialized,
            gate.ensureInitialized(31_001L, source, initializer),
        )
        assertEquals(2, attempts)
    }

    @Test
    fun `nullable configuration value still initializes empty state once`() {
        val gate = ConfigurationLoadGate(retryDelayMs = 30_000L)
        var sourceReads = 0
        var initializationAttempts = 0

        val first = gate.ensureInitialized<String?>(0L, source = {
            sourceReads++
            null
        }) {
            initializationAttempts++
            true
        }
        val second = gate.ensureInitialized<String?>(1L, source = {
            sourceReads++
            null
        }) {
            initializationAttempts++
            true
        }

        assertEquals(ConfigurationLoadResult.Initialized, first)
        assertEquals(ConfigurationLoadResult.AlreadyInitialized, second)
        assertEquals(1, sourceReads)
        assertEquals(1, initializationAttempts)
    }
}
