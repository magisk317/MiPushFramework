package io.github.magisk317.mipush.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Path
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ThemePreferenceRepositoryTest {
    @TempDir
    lateinit var tempDirectory: Path

    @Test
    fun `theme appearance defaults preserve existing dynamic color behavior`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = PreferenceRepository(
                PreferenceDataStoreFactory.create(scope = scope) {
                    tempDirectory.resolve("theme-defaults.preferences_pb").toFile()
                },
            )
            assertTrue(repository.themeDynamicColor.first())
            assertEquals(0, repository.themeAccentColor.first())
            assertFalse(repository.themeMonetEnabled.first())
            assertEquals(0, repository.themePaletteStyle.first())
            assertEquals(1, repository.themeColorSpec.first())
            assertFalse(repository.themeSurfaceBlur.first())
            assertEquals(1, repository.uiLayoutScale.first())
            assertTrue(repository.navigationFloatingBottomBar.first())
            assertFalse(repository.navigationBottomBarBlur.first())
            assertFalse(repository.navigationBottomBarBackdrop.first())
            assertTrue(repository.navigationBadges.first())
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun `theme appearance setters persist dynamic color and accent`() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        try {
            val repository = PreferenceRepository(
                PreferenceDataStoreFactory.create(scope = scope) {
                    tempDirectory.resolve("theme-persisted.preferences_pb").toFile()
                },
            )
            repository.setThemeDynamicColor(false)
            repository.setThemeAccentColor(0xFF6750A4.toInt())
            repository.setThemeMonetEnabled(true)
            repository.setThemePaletteStyle(8)
            repository.setThemeColorSpec(0)
            repository.setThemeSurfaceBlur(true)
            repository.setUiLayoutScale(2)
            repository.setNavigationFloatingBottomBar(true)
            repository.setNavigationBottomBarBlur(true)
            repository.setNavigationBottomBarBackdrop(true)
            repository.setNavigationBadges(false)
            assertEquals(false, repository.themeDynamicColor.first())
            assertEquals(0xFF6750A4.toInt(), repository.themeAccentColor.first())
            assertTrue(repository.themeMonetEnabled.first())
            assertEquals(8, repository.themePaletteStyle.first())
            assertEquals(0, repository.themeColorSpec.first())
            assertTrue(repository.themeSurfaceBlur.first())
            assertEquals(2, repository.uiLayoutScale.first())
            repository.setThemePaletteStyle(99)
            repository.setThemeColorSpec(99)
            assertEquals(0, repository.themePaletteStyle.first())
            assertEquals(1, repository.themeColorSpec.first())
            repository.setUiLayoutScale(99)
            assertEquals(1, repository.uiLayoutScale.first())
            assertTrue(repository.navigationFloatingBottomBar.first())
            assertTrue(repository.navigationBottomBarBlur.first())
            assertTrue(repository.navigationBottomBarBackdrop.first())
            assertFalse(repository.navigationBadges.first())
        } finally {
            scope.cancel()
        }
    }
}
