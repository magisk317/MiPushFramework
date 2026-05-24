package io.github.magisk317.mipush.feature.main

import io.github.magisk317.mipush.feature.navigation.AppDestinations
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainScrollChromeStateTest {

    @Test
    fun `down scroll hides and up scroll shows chrome`() {
        val state = MainScrollChromeState()

        assertTrue(state.isChromeVisible)

        state.onScrollDelta(delta = 1)
        assertFalse(state.isChromeVisible)

        state.onScrollDelta(delta = -1)
        assertTrue(state.isChromeVisible)
    }

    @Test
    fun `top position always shows chrome`() {
        val state = MainScrollChromeState()

        state.onScrollDelta(delta = 1)
        assertFalse(state.isChromeVisible)

        state.onScrollDelta(delta = 1, atTop = true)
        assertTrue(state.isChromeVisible)
    }

    @Test
    fun `overview route keeps main chrome visible`() {
        assertTrue(
            shouldKeepMainChromeVisible(
                route = AppDestinations.Overview.ROUTE,
                chromeVisible = false,
            ),
        )
        assertFalse(
            shouldKeepMainChromeVisible(
                route = AppDestinations.AppsList.ROUTE,
                chromeVisible = false,
            ),
        )
    }

    @Test
    fun `bottom gesture scrim follows compact bottom bar visibility`() {
        assertTrue(
            shouldShowBottomGestureScrim(
                isCompact = true,
                compactBottomBarAvailable = true,
                compactBottomBarVisible = true,
            ),
        )
        assertFalse(
            shouldShowBottomGestureScrim(
                isCompact = true,
                compactBottomBarAvailable = true,
                compactBottomBarVisible = false,
            ),
        )
        assertFalse(
            shouldShowBottomGestureScrim(
                isCompact = false,
                compactBottomBarAvailable = true,
                compactBottomBarVisible = true,
            ),
        )
    }
}
