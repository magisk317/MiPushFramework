package io.github.magisk317.mipush.hook.island

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class IslandPreferencesTest {
    @BeforeEach
    fun reset() {
        IslandPreferences.resetForTest()
    }

    @Test
    fun `default options keep focus payload injection disabled`() {
        val options = IslandPreferences.current()

        assertTrue(options.enabled)
        assertFalse(options.focusNotification)
        assertFalse(options.canInjectFocusPayload)
        assertEquals(5, options.timeoutSecs)
    }

    @Test
    fun `focus payload requires both total switch and focus switch`() {
        assertFalse(
            IslandOptions(enabled = false, focusNotification = true).canInjectFocusPayload
        )
        assertFalse(
            IslandOptions(enabled = true, focusNotification = false).canInjectFocusPayload
        )
        assertTrue(
            IslandOptions(enabled = true, focusNotification = true).canInjectFocusPayload
        )
    }

    @Test
    fun `refresh keeps stale package value until asynchronous replacement arrives`() {
        val packageName = "example.app"
        val packageOptions = IslandOptions(enabled = false, showNotification = false)
        IslandPreferences.cachePackageOptionsForTest(packageName, packageOptions)

        val refresh = IslandPreferences.prepareRefresh()

        assertEquals(packageOptions, IslandPreferences.current(packageName))
        assertTrue(packageName in refresh.packageNames)
    }

    @Test
    fun `uncached package does not inherit globally enabled focus mode`() {
        IslandPreferences.resetForTest(
            IslandOptions(enabled = true, enableFloat = true, focusNotification = true)
        )

        assertFalse(IslandPreferences.current("uncached.app").canInjectFocusPayload)
    }
}
