package io.github.magisk317.mipush.hook.island

import io.github.magisk317.mipush.common.ISLAND_PREF_ENABLED
import io.github.magisk317.mipush.common.ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * B1 xposed-side convergence: showOriginalNotification is forced true while the island is disabled,
 * so suppressing the original can never leave the user with zero notifications.
 */
class IslandPreferencesShowOriginalTest {
    @Test
    fun `island disabled forces showOriginalNotification true`() {
        val options = IslandPreferences.buildOptionsFromValues(
            mapOf(
                ISLAND_PREF_ENABLED to "0",
                ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION to "0",
            ),
        )
        assertTrue(options.showOriginalNotification) {
            "island disabled must treat showOriginalNotification as true to avoid silent loss"
        }
    }

    @Test
    fun `island enabled preserves showOriginalNotification preference`() {
        assertFalse(
            IslandPreferences.buildOptionsFromValues(
                mapOf(
                    ISLAND_PREF_ENABLED to "1",
                    ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION to "0",
                ),
            ).showOriginalNotification,
        )
        assertTrue(
            IslandPreferences.buildOptionsFromValues(
                mapOf(
                    ISLAND_PREF_ENABLED to "1",
                    ISLAND_PREF_SHOW_ORIGINAL_NOTIFICATION to "1",
                ),
            ).showOriginalNotification,
        )
    }
}
