package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.manager.application.ManagerApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ApplicationInfoStatePolicyTest {
    @Test
    fun `valid Xiaomi package exposes all controls while unblocked`() {
        val info = app(
            packageName = "com.xiaomi.smarthome",
            islandEnabled = true,
        )

        val controls = ApplicationInfoStatePolicy.controls(info)

        assertTrue(controls.zygiskConfigurable)
        assertTrue(controls.recentActivityEnabled)
        assertTrue(controls.islandEnabled)
        assertTrue(controls.islandFocusEnabled)
    }

    @Test
    fun `blocked app disables every control except the block toggle`() {
        val info = app(
            blocked = true,
            islandEnabled = true,
            islandFocusNotification = true,
        )

        val controls = ApplicationInfoStatePolicy.controls(info)

        assertFalse(controls.zygiskConfigurable)
        assertFalse(controls.recentActivityEnabled)
        assertFalse(controls.islandEnabled)
        assertFalse(controls.islandFocusEnabled)
        assertNull(ApplicationInfoStatePolicy.withIslandEnabled(info, enabled = true))
        assertNull(ApplicationInfoStatePolicy.withIslandFocusEnabled(info, enabled = true))
    }

    @Test
    fun `blocking clears island switches and unblocking does not silently restore them`() {
        val enabled = app(
            islandEnabled = true,
            islandFocusNotification = true,
        )

        val blocked = ApplicationInfoStatePolicy.withBlocked(enabled, blocked = true)
        val unblocked = ApplicationInfoStatePolicy.withBlocked(blocked, blocked = false)

        assertTrue(blocked.blocked)
        assertFalse(blocked.islandEnabled)
        assertFalse(blocked.islandFocusNotification)
        assertFalse(unblocked.blocked)
        assertFalse(unblocked.islandEnabled)
        assertFalse(unblocked.islandFocusNotification)
    }

    @Test
    fun `turning island off also clears focus notification`() {
        val info = app(
            islandEnabled = true,
            islandFocusNotification = true,
        )

        val updated = ApplicationInfoStatePolicy.withIslandEnabled(info, enabled = false)

        requireNotNull(updated)
        assertFalse(updated.islandEnabled)
        assertFalse(updated.islandFocusNotification)
    }

    @Test
    fun `focus cannot be enabled until island is enabled`() {
        val disabled = app(islandEnabled = false)
        assertNull(ApplicationInfoStatePolicy.withIslandFocusEnabled(disabled, enabled = true))

        val enabled = ApplicationInfoStatePolicy.withIslandEnabled(disabled, enabled = true)
        requireNotNull(enabled)
        val focused = ApplicationInfoStatePolicy.withIslandFocusEnabled(enabled, enabled = true)

        requireNotNull(focused)
        assertEquals(true, focused.islandFocusNotification)
    }

    @Test
    fun `invalid package is never Zygisk configurable`() {
        val controls = ApplicationInfoStatePolicy.controls(app(packageName = "not-a-package"))
        assertFalse(controls.zygiskConfigurable)
    }

    private fun app(
        packageName: String = "com.example.app",
        blocked: Boolean = false,
        islandEnabled: Boolean = false,
        islandFocusNotification: Boolean = false,
    ) = ManagerApplication(
        packageName = packageName,
        blocked = blocked,
        islandEnabled = islandEnabled,
        islandFocusNotification = islandFocusNotification,
    )
}
