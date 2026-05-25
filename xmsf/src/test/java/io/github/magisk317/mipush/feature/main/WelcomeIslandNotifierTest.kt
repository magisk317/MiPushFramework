package io.github.magisk317.mipush.feature.main

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class WelcomeIslandNotifierTest {
    @Test
    fun `first open after install should notify`() {
        assertTrue(WelcomeIslandNotifier.shouldNotifyForInstall(100L, 0L))
    }

    @Test
    fun `same install should not notify again`() {
        assertFalse(WelcomeIslandNotifier.shouldNotifyForInstall(100L, 100L))
    }

    @Test
    fun `upgrade should notify again`() {
        assertTrue(WelcomeIslandNotifier.shouldNotifyForInstall(200L, 100L))
    }
}
