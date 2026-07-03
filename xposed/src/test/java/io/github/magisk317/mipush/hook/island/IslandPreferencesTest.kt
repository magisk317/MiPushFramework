package io.github.magisk317.mipush.hook.island

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IslandPreferencesTest {
    @BeforeEach
    fun reset() {
        IslandPreferences.resetForTest()
    }

    @Test
    fun `default options keep focus payload injection disabled`() {
        val options = IslandPreferences.current()

        assertTrue(options.enabled)
        assertFalse(options.focusNotification)
        assertFalse(options.canInjectFocusPayload)
        assertEquals(5, options.timeoutSecs)
    }

    @Test
    fun `focus payload requires both total switch and focus switch`() {
        assertFalse(
            IslandOptions(enabled = false, focusNotification = true).canInjectFocusPayload
        )
        assertFalse(
            IslandOptions(enabled = true, focusNotification = false).canInjectFocusPayload
        )
        assertTrue(
            IslandOptions(enabled = true, focusNotification = true).canInjectFocusPayload
        )
    }
}
