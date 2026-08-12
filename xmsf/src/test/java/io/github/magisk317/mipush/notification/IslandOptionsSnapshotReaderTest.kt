package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.data.IslandSettingsSnapshot
import io.github.magisk317.mipush.common.island.IslandRendererMode
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class IslandOptionsSnapshotReaderTest {
    @AfterEach
    fun clearSettingsCache() {
        IslandOptionsSnapshotReader.clearCachedSettings()
    }

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

    @Test
    fun `global reads use the cached settings after initialization`() {
        val context = org.robolectric.RuntimeEnvironment.getApplication()
        IslandOptionsSnapshotReader.updateCachedSettings(
            settings(
                enabled = false,
                timeoutSecs = 17,
                rendererMode = IslandRendererMode.HYPERISLAND.wireValue,
            ),
        )

        val snapshot = IslandOptionsSnapshotReader.read(context)

        assertFalse(snapshot.options.enabled)
        assertEquals(17, snapshot.options.timeoutSecs)
        assertEquals(IslandRendererMode.HYPERISLAND, snapshot.options.rendererMode)
    }

    private fun settings(
        enabled: Boolean = true,
        timeoutSecs: Int = 5,
        focusNotification: Boolean = false,
        colorStatusBarIcon: Boolean = false,
        colorStatusBarIconGlobal: Boolean = false,
        dualAppEnabled: Boolean = false,
        logSanitizationEnabled: Boolean = false,
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
        colorStatusBarIcon = colorStatusBarIcon,
        colorStatusBarIconGlobal = colorStatusBarIconGlobal,
        dualAppEnabled = dualAppEnabled,
        logSanitizationEnabled = logSanitizationEnabled,
        rendererMode = rendererMode,
        visualEnabled = visualEnabled,
        dynamicColor = dynamicColor,
        blurEnabled = blurEnabled,
        glassEnabled = glassEnabled,
        outerGlowEnabled = outerGlowEnabled,
        animationEnabled = animationEnabled,
    )
}
