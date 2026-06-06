package io.github.magisk317.mipush.hook.island

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IslandOptionsTest {
    @Test
    fun `default options keep both focus and original notifications visible`() {
        val options = IslandOptions()

        assertTrue(options.showNotification)
        assertTrue(options.showOriginalNotification)
    }

    @Test
    fun `canInjectFocusPayload stays true regardless of show fields`() {
        val options = IslandOptions(
            enabled = true,
            focusNotification = true,
            enableFloat = true,
            showNotification = false,
            showOriginalNotification = false,
        )

        assertTrue(options.canInjectFocusPayload)
    }

    @Test
    fun `field defaults match the historical default true`() {
        val options = IslandOptions()
        // After the split, both the focus-notification and original-notification
        // switches must default to true so the user keeps the legacy behavior
        // of seeing both notifications after a fresh install.
        assertEquals(true, options.showNotification)
        assertEquals(true, options.showOriginalNotification)
    }

    @Test
    fun `canInjectFocusPayload respects enableFloat=false alone`() {
        val options = IslandOptions(enableFloat = false)

        assertFalse(options.canInjectFocusPayload)
    }
}
