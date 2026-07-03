package io.github.magisk317.mipush.hook.island

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IslandOptionsTest {
    @Test
    fun `default options keep generated and original notifications visible`() {
        val options = IslandOptions()

        assertTrue(options.showNotification)
        assertTrue(options.showOriginalNotification)
        assertFalse(options.focusNotification)
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
    fun `field defaults keep focus notification opt in`() {
        val options = IslandOptions()
        assertEquals(true, options.showNotification)
        assertEquals(true, options.showOriginalNotification)
        assertEquals(false, options.focusNotification)
    }

    @Test
    fun `canInjectFocusPayload respects enableFloat=false alone`() {
        val options = IslandOptions(enableFloat = false)

        assertFalse(options.canInjectFocusPayload)
    }
}
