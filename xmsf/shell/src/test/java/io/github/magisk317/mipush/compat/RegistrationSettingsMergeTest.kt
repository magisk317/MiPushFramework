package io.github.magisk317.mipush.compat

import io.github.magisk317.mipush.app.di.mergeEditableApplicationSettings
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RegistrationSettingsMergeTest {
    @Test
    fun `saving stale allowed settings preserves the latest denial and unregistered state`() {
        val revoked = RuntimeRegisteredApplicationRow(
            id = 7L,
            packageName = "app.pkg",
            userId = 0,
            type = 3,
            blocked = true,
            notificationOnRegister = false,
            registeredType = RegisteredAppRegisteredType.Unregistered,
            appName = "App",
        )
        val stale = ManagerApplication(
            id = 7L,
            packageName = "app.pkg",
            type = 2,
            blocked = false,
            registeredType = ManagerApplication.RegisteredType.REGISTERED,
            islandEnabled = true,
            islandFocusNotification = true,
            clickFallbackEnabled = true,
            countLimitExempt = true,
            notificationOnRegister = true,
        )

        val saved = mergeEditableApplicationSettings(revoked, stale)

        assertEquals(3, saved.type)
        assertEquals(true, saved.blocked)
        assertEquals(RegisteredAppRegisteredType.Unregistered, saved.registeredType)
        assertEquals(true, saved.notificationOnRegister)
        assertFalse(saved.islandEnabled)
        assertFalse(saved.islandFocusNotification)
        assertFalse(saved.clickFallbackEnabled)
        assertFalse(saved.countLimitExempt)
    }
}
