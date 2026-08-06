package io.github.magisk317.mipush.hook.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class XSpacePackageSyncHookTest {
    @Test
    fun `resolves xspace user from package broadcast`() {
        assertEquals(999, XSpacePackageSyncHook.resolveUserId(userHandle = 999, uid = null))
    }

    @Test
    fun `resolves xspace user from uid when user handle is absent`() {
        assertEquals(999, XSpacePackageSyncHook.resolveUserId(userHandle = null, uid = 99910209))
    }

    @Test
    fun `missing user data is ignored`() {
        assertNull(XSpacePackageSyncHook.resolveUserId(userHandle = null, uid = null))
    }

    @Test
    fun `builds mirrored xmsf package actions`() {
        assertEquals(XSpacePackageSyncHook.PackageSyncAction.InstallExisting, XSpacePackageSyncHook.installXmsfAction())
        assertEquals(XSpacePackageSyncHook.PackageSyncAction.UninstallExisting, XSpacePackageSyncHook.uninstallXmsfAction())
        assertEquals(true, XSpacePackageSyncHook.installXmsfAction().expectedInstalled)
        assertEquals(false, XSpacePackageSyncHook.uninstallXmsfAction().expectedInstalled)
    }

    @Test
    fun `rejects registration from a stopped generation`() {
        assertEquals(
            false,
            XSpacePackageSyncHook.acceptsRegistration(
                expectedGeneration = 7L,
                currentGeneration = 8L,
                installed = true,
            ),
        )
    }

    @Test
    fun `accepts registration only for the current installed generation`() {
        assertEquals(
            true,
            XSpacePackageSyncHook.acceptsRegistration(
                expectedGeneration = 7L,
                currentGeneration = 7L,
                installed = true,
            ),
        )
        assertEquals(
            false,
            XSpacePackageSyncHook.acceptsRegistration(
                expectedGeneration = 7L,
                currentGeneration = 7L,
                installed = false,
            ),
        )
    }
}
