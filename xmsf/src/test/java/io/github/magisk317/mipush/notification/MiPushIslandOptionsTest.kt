package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushIslandOptionsTest {
    @Test
    fun `default options keep both focus and original notifications visible`() {
        val options = MiPushIslandOptions()

        assertTrue(options.showNotification)
        assertTrue(options.showOriginalNotification)
    }

    @Test
    fun `canBuildFocusPayload stays true regardless of show fields`() {
        val options = MiPushIslandOptions(
            enabled = true,
            focusNotification = true,
            enableFloat = true,
            showNotification = false,
            showOriginalNotification = false,
        )

        assertTrue(options.canBuildFocusPayload)
    }

    @Test
    fun `field defaults match the historical default true`() {
        val options = MiPushIslandOptions()
        // Both the focus-notification and original-notification switches default
        // to true so the user keeps the legacy behavior of seeing both
        // notifications after a fresh install.
        assertEquals(true, options.showNotification)
        assertEquals(true, options.showOriginalNotification)
    }
}
