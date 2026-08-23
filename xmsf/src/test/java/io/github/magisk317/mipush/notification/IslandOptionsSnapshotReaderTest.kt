package io.github.magisk317.mipush.notification

import io.github.magisk317.mipush.common.island.IslandRendererMode
import io.github.magisk317.mipush.data.IslandSettingsSnapshot
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
        enabled: Boolean,
        timeoutSecs: Int,
        rendererMode: String,
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
        rendererMode = rendererMode,
        visualEnabled = true,
        dynamicColor = true,
        blurEnabled = true,
        glassEnabled = true,
        outerGlowEnabled = true,
        animationEnabled = true,
    )
}
