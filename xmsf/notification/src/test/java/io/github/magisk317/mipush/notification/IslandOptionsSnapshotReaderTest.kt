package io.github.magisk317.mipush.notification

import android.content.Context
import io.github.magisk317.mipush.data.IslandSettingsSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class IslandOptionsSnapshotReaderTest {
    @AfterEach
    fun clearSettingsCache() {
        IslandOptionsSnapshotReader.clearCachedSettings()
    }

    @Test
    fun `global reads use the cached settings after initialization`() {
        val context = mockk<Context> {
            every { applicationContext } returns mockk(relaxed = true)
        }
        IslandOptionsSnapshotReader.updateCachedSettings(
            settings(
                enabled = false,
                timeoutSecs = 17,
            ),
        )

        val snapshot = IslandOptionsSnapshotReader.read(context)

        assertFalse(snapshot.options.enabled)
        assertEquals(17, snapshot.options.timeoutSecs)
    }

    private fun settings(
        enabled: Boolean,
        timeoutSecs: Int,
    ) = IslandSettingsSnapshot(
        enabled = enabled,
        timeoutSecs = timeoutSecs,
        firstFloat = true,
        enableFloat = true,
        showNotification = true,
        showOriginalNotification = true,
        focusNotification = false,
        colorStatusBarIcon = false,
        colorStatusBarIconGlobal = false,
        dualAppEnabled = false,
        logSanitizationEnabled = false,
    )
}
