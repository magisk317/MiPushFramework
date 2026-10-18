package io.github.magisk317.mipush.hook.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NmsPermissionHookerTest {
    @Test
    fun `cancel keeps xmsf opPkg for xmsf-owned notifications`() {
        assertEquals(
            "com.xiaomi.xmsf",
            NmsPermissionHooker.cancelOpPackage(
                pkg = "com.xiaomi.xmsf",
                currentOpPkg = "com.xiaomi.xmsf",
            ),
        )
        assertEquals(
            "com.xiaomi.xmsf",
            NmsPermissionHooker.cancelOpPackage(
                pkg = "com.example.target",
                currentOpPkg = "com.xiaomi.xmsf",
            ),
        )
    }

    @Test
    fun `cancel rewrites foreign opPkg to android system identity`() {
        assertEquals(
            "android",
            NmsPermissionHooker.cancelOpPackage(
                pkg = "com.example.target",
                currentOpPkg = null,
            ),
        )
        assertEquals(
            "android",
            NmsPermissionHooker.cancelOpPackage(
                pkg = null,
                currentOpPkg = "com.other.app",
            ),
        )
    }

    @Test
    fun `recognizes cloned user xmsf by calling packages`() {
        assertTrue(
            NmsPermissionHooker.isXmsfCallingIdentity(
                callingUid = 99910209,
                primaryXmsfUid = 10209,
                callingPackages = listOf("com.xiaomi.xmsf"),
            ),
        )
    }

    @Test
    fun `recognizes primary user xmsf by uid`() {
        assertTrue(
            NmsPermissionHooker.isXmsfCallingIdentity(
                callingUid = 10209,
                primaryXmsfUid = 10209,
                callingPackages = emptyList(),
            ),
        )
    }

    @Test
    fun `rejects non xmsf callers`() {
        assertFalse(
            NmsPermissionHooker.isXmsfCallingIdentity(
                callingUid = 99912000,
                primaryXmsfUid = 10209,
                callingPackages = listOf("com.example.app"),
            ),
        )
    }

    @Test
    fun `preserves delegate identity only for an authenticated xmsf caller`() {
        assertTrue(
            NmsPermissionHooker.shouldPreserveXmsfDelegateIdentity(
                callingPackage = "com.xiaomi.xmsf",
                callingUid = 99910209,
                primaryXmsfUid = 10209,
                callingPackages = listOf("com.xiaomi.xmsf"),
            ),
        )
        assertFalse(
            NmsPermissionHooker.shouldPreserveXmsfDelegateIdentity(
                callingPackage = "android",
                callingUid = 99910209,
                primaryXmsfUid = 10209,
                callingPackages = listOf("com.xiaomi.xmsf"),
            ),
        )
        assertFalse(
            NmsPermissionHooker.shouldPreserveXmsfDelegateIdentity(
                callingPackage = "com.xiaomi.xmsf",
                callingUid = 99912000,
                primaryXmsfUid = 10209,
                callingPackages = listOf("com.example.app"),
            ),
        )
    }

    @Test
    fun `keeps xmsf calling identity when the target package is xmsf itself`() {
        assertFalse(
            NmsPermissionHooker.shouldBypassCallingIdentity(targetPackageName = "com.xiaomi.xmsf"),
        )
    }

    @Test
    fun `bypasses calling identity for foreign target packages`() {
        assertTrue(
            NmsPermissionHooker.shouldBypassCallingIdentity(targetPackageName = "com.example.target"),
        )
        assertTrue(
            NmsPermissionHooker.shouldBypassCallingIdentity(targetPackageName = null),
        )
    }
}
