package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.common.island.IslandRendererMode
import io.github.magisk317.mipush.data.IslandSettingsSnapshot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class IslandOptionsSnapshotReaderPolicyTest {
    @AfterEach
    fun clearSettingsCache() {
        IslandOptionsSnapshotReader.clearCachedSettings()
    }

    @Test
    fun `merge preserves global options and clamps timeout`() {
        val snapshot = IslandOptionsSnapshotReader.merge(
            settings = settings(timeoutSecs = 0),
            appEnabled = null,
            appFocusNotification = null,
        )

        assertEquals(1, snapshot.options.timeoutSecs)
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
        val allowed = IslandOptionsSnapshotReader.merge(
            settings = settings(focusNotification = true),
            appEnabled = null,
            appFocusNotification = true,
            packageScoped = true,
        )
        val denied = IslandOptionsSnapshotReader.merge(
            settings = settings(focusNotification = true),
            appEnabled = null,
            appFocusNotification = false,
            packageScoped = true,
        )

        assertTrue(allowed.options.focusNotification)
        assertFalse(denied.options.focusNotification)
    }

    @Test
    fun `unscoped focus snapshot preserves the global authorization switch`() {
        val snapshot = IslandOptionsSnapshotReader.merge(
            settings = settings(focusNotification = true),
            appEnabled = null,
            appFocusNotification = null,
            packageScoped = false,
        )

        assertTrue(snapshot.options.focusNotification)
    }

    @Test
    fun `visual settings cross the runtime snapshot without changing notification semantics`() {
        val snapshot = IslandOptionsSnapshotReader.merge(
            settings = settings(
                rendererMode = IslandRendererMode.HYPERISLAND.wireValue,
                visualEnabled = false,
                dynamicColor = false,
                blurEnabled = false,
                glassEnabled = false,
                outerGlowEnabled = false,
                animationEnabled = false,
            ),
            appEnabled = null,
            appFocusNotification = null,
        )

        assertEquals(IslandRendererMode.HYPERISLAND, snapshot.options.rendererMode)
        assertFalse(snapshot.options.visualEnabled)
        assertFalse(snapshot.options.dynamicColor)
        assertFalse(snapshot.options.blurEnabled)
        assertFalse(snapshot.options.glassEnabled)
        assertFalse(snapshot.options.outerGlowEnabled)
        assertFalse(snapshot.options.animationEnabled)
        assertTrue(snapshot.options.showNotification)
    }

    @Test
    fun `unavailable settings disable island proxy but preserve original notification`() {
        val snapshot = IslandOptionsSnapshotReader.unavailableSnapshot()

        assertFalse(snapshot.options.enabled)
        assertFalse(snapshot.options.showNotification)
        assertTrue(snapshot.options.showOriginalNotification)
        assertFalse(snapshot.options.focusNotification)
    }

    private fun settings(
        enabled: Boolean = true,
        timeoutSecs: Int = 5,
        focusNotification: Boolean = false,
        rendererMode: String = "auto",
        visualEnabled: Boolean = true,
        dynamicColor: Boolean = true,
        blurEnabled: Boolean = true,
        glassEnabled: Boolean = true,
        outerGlowEnabled: Boolean = true,
        animationEnabled: Boolean = true,
    ) = IslandSettingsSnapshot(
        enabled = enabled,
        timeoutSecs = timeoutSecs,
        firstFloat = true,
        enableFloat = true,
        showNotification = true,
        showOriginalNotification = true,
        focusNotification = focusNotification,
        colorStatusBarIcon = false,
        colorStatusBarIconGlobal = false,
        dualAppEnabled = false,
        logSanitizationEnabled = false,
        rendererMode = rendererMode,
        visualEnabled = visualEnabled,
        dynamicColor = dynamicColor,
        blurEnabled = blurEnabled,
        glassEnabled = glassEnabled,
        outerGlowEnabled = outerGlowEnabled,
        animationEnabled = animationEnabled,
    )
}
