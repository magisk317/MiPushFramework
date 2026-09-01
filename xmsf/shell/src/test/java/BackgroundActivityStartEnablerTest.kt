package io.github.magisk317.mipush.service.runtime

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BackgroundActivityStartEnablerTest {
    @Test
    fun `only the initialization tag and id are capturable`() {
        assertTrue(BackgroundActivityStartEnabler.isInitializingNotification(0, "MPF.BAFE"))
        assertFalse(BackgroundActivityStartEnabler.isInitializingNotification(1, "MPF.BAFE"))
        assertFalse(BackgroundActivityStartEnabler.isInitializingNotification(0, "other"))
        assertFalse(BackgroundActivityStartEnabler.isInitializingNotification(0, null))
    }

    @Test
    fun `initialization notification wins over existing donor`() {
        val candidates = listOf(
            BackgroundActivityStartEnabler.NotificationCaptureCandidate(7, "existing", true),
            BackgroundActivityStartEnabler.NotificationCaptureCandidate(0, "MPF.BAFE", false),
        )

        assertTrue(BackgroundActivityStartEnabler.selectCaptureCandidate(candidates) == 1)
    }

    @Test
    fun `existing notification requires pending intent and is not selected by identity`() {
        val candidates = listOf(
            BackgroundActivityStartEnabler.NotificationCaptureCandidate(1, "no-intent", false),
            BackgroundActivityStartEnabler.NotificationCaptureCandidate(2, "donor", true),
        )

        assertTrue(BackgroundActivityStartEnabler.selectCaptureCandidate(candidates) == 1)
        assertTrue(
            BackgroundActivityStartEnabler.selectCaptureCandidate(
                listOf(BackgroundActivityStartEnabler.NotificationCaptureCandidate(1, null, false)),
            ) == -1,
        )
    }
}
