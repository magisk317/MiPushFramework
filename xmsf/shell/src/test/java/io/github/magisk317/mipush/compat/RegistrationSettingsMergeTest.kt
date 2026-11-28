package io.github.magisk317.mipush.compat

import io.github.magisk317.mipush.app.di.mergeEditableApplicationSettings
import io.github.magisk317.mipush.manager.application.ManagerApplication
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationSettingsMergeTest {
    @Test
    fun `native live update setting follows the app block boundary`() {
        val latest = RuntimeRegisteredApplicationRow(
            id = 7L,
            packageName = "app.pkg",
            userId = 0,
            type = 2,
            notificationOnRegister = true,
            registeredType = RegisteredAppRegisteredType.Registered,
            appName = "App",
        )
        val enabled = mergeEditableApplicationSettings(
            latest,
            ManagerApplication(packageName = "app.pkg", nativeLiveUpdateEnabled = true),
        )
        assertTrue(enabled.nativeLiveUpdateEnabled)

        val blocked = mergeEditableApplicationSettings(
            latest.copy(blocked = true, nativeLiveUpdateEnabled = true),
            ManagerApplication(packageName = "app.pkg", nativeLiveUpdateEnabled = false),
        )
        assertTrue(blocked.nativeLiveUpdateEnabled)
        assertFalse(
            mergeEditableApplicationSettings(
                latest.copy(blocked = true, nativeLiveUpdateEnabled = false),
                ManagerApplication(packageName = "app.pkg", nativeLiveUpdateEnabled = true),
            ).nativeLiveUpdateEnabled,
        )
    }

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
            islandEnabled = true,
            islandFocusNotification = true,
            clickFallbackEnabled = true,
            suppressWhenDisabled = true,
            countLimitExempt = true,
            nativeLiveUpdateEnabled = true,
        )
        val stale = ManagerApplication(
            id = 7L,
            packageName = "app.pkg",
            type = 2,
            blocked = false,
            registeredType = ManagerApplication.RegisteredType.REGISTERED,
            islandEnabled = false,
            islandFocusNotification = false,
            clickFallbackEnabled = false,
            suppressWhenDisabled = false,
            countLimitExempt = false,
            nativeLiveUpdateEnabled = false,
            notificationOnRegister = true,
        )

        val saved = mergeEditableApplicationSettings(revoked, stale)

        assertEquals(3, saved.type)
        assertEquals(true, saved.blocked)
        assertEquals(RegisteredAppRegisteredType.Unregistered, saved.registeredType)
        assertEquals(true, saved.notificationOnRegister)
        assertTrue(saved.islandEnabled)
        assertTrue(saved.islandFocusNotification)
        assertTrue(saved.clickFallbackEnabled)
        assertTrue(saved.countLimitExempt)
        assertTrue(saved.nativeLiveUpdateEnabled)
        assertTrue(saved.suppressWhenDisabled)
    }
}
