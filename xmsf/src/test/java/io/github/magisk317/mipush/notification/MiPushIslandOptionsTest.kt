package io.github.magisk317.mipush.notification

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushIslandOptionsTest {
    @Test
    fun `default options keep generated and original notifications visible`() {
        val options = MiPushIslandOptions()

        assertTrue(options.showNotification)
        assertTrue(options.showOriginalNotification)
        assertEquals(false, options.focusNotification)
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
    fun `field defaults keep focus notification opt in`() {
        val options = MiPushIslandOptions()
        assertEquals(true, options.showNotification)
        assertEquals(true, options.showOriginalNotification)
        assertEquals(false, options.focusNotification)
    }
}
