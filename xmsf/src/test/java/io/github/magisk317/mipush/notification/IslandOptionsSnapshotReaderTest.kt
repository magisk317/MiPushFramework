package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.data.IslandSettingsSnapshot
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IslandOptionsSnapshotReaderTest {
    @Test
    fun `merge preserves global options and clamps timeout`() {
        val snapshot = IslandOptionsSnapshotReader.merge(
            settings = settings(
                timeoutSecs = 0,
                colorStatusBarIcon = true,
                colorStatusBarIconGlobal = true,
                logSanitizationEnabled = true,
            ),
            appEnabled = null,
            appFocusNotification = null,
        )

        assertEquals(1, snapshot.options.timeoutSecs)
        assertTrue(snapshot.options.colorStatusBarIcon)
        assertTrue(snapshot.options.colorStatusBarIconGlobal)
        assertTrue(snapshot.logSanitizationEnabled)
    }

    @Test
    fun `per-app enable can only narrow the global setting`() {
        assertFalse(
            IslandOptionsSnapshotReader.merge(
                settings = settings(enabled = true),
                appEnabled = false,
                appFocusNotification = null,
            ).options.enabled,
        )
        assertFalse(
            IslandOptionsSnapshotReader.merge(
                settings = settings(enabled = false),
                appEnabled = true,
                appFocusNotification = null,
            ).options.enabled,
        )
    }

    @Test
    fun `package-scoped focus requires both global and per-app settings`() {
        assertTrue(
            IslandOptionsSnapshotReader.merge(
                settings = settings(focusNotification = true),
                appEnabled = null,
                appFocusNotification = true,
                packageScoped = true,
            ).options.focusNotification,
        )
        assertFalse(
            IslandOptionsSnapshotReader.merge(
                settings = settings(focusNotification = true),
                appEnabled = null,
                appFocusNotification = false,
                packageScoped = true,
            ).options.focusNotification,
        )
        assertFalse(
            IslandOptionsSnapshotReader.merge(
                settings = settings(focusNotification = false),
                appEnabled = null,
                appFocusNotification = true,
                packageScoped = true,
            ).options.focusNotification,
        )
    }

    @Test
    fun `unscoped focus snapshot preserves the global authorization switch`() {
        assertTrue(
            IslandOptionsSnapshotReader.merge(
                settings = settings(focusNotification = true),
                appEnabled = null,
                appFocusNotification = null,
                packageScoped = false,
            ).options.focusNotification,
        )
    }

    private fun settings(
        enabled: Boolean = true,
        timeoutSecs: Int = 5,
        focusNotification: Boolean = false,
        colorStatusBarIcon: Boolean = false,
        colorStatusBarIconGlobal: Boolean = false,
        dualAppEnabled: Boolean = false,
        logSanitizationEnabled: Boolean = false,
    ) = IslandSettingsSnapshot(
        enabled = enabled,
        timeoutSecs = timeoutSecs,
        firstFloat = true,
        enableFloat = true,
        showNotification = true,
        showOriginalNotification = true,
        focusNotification = focusNotification,
        colorStatusBarIcon = colorStatusBarIcon,
        colorStatusBarIconGlobal = colorStatusBarIconGlobal,
        dualAppEnabled = dualAppEnabled,
        logSanitizationEnabled = logSanitizationEnabled,
    )
}
