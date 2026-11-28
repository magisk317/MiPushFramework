package io.github.magisk317.mipush.hook.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NmsPermissionHookerTest {
    @Test
    fun `target user is derived from per user uid`() {
        assertEquals(0, NmsPermissionHooker.userIdFromUid(1000))
        assertEquals(11, NmsPermissionHooker.userIdFromUid(1_110_259))
    }

    @Test
    fun `synthetic system identity keeps target user and pid`() {
        val identity = NmsPermissionHooker.systemIdentityForUser(userId = 11, pid = 1234)
        assertEquals(1_101_000, (identity ushr 32).toInt())
        assertEquals(1234, identity.toInt())
    }

    @Test
    fun `foreign target gets a target user identity for cloned users`() {
        val identity = NmsPermissionHooker.systemIdentityForTarget(
            targetPackageName = "com.example.target",
            targetUserId = 11,
            pid = 1234,
        )

        assertEquals(1_101_000, (identity!! ushr 32).toInt())
    }

    @Test
    fun `target uid takes precedence over explicit target user`() {
        assertEquals(
            11,
            NmsPermissionHooker.resolveTargetUserId(
                args = arrayOf<Any?>("pkg", 1_110_259, null, 0),
                callingUid = 10209,
                targetUidParamIndex = 1,
                targetUserIdParamIndex = 3,
            ),
        )
    }

    @Test
    fun `explicit target user is used when target uid is missing`() {
        assertEquals(
            11,
            NmsPermissionHooker.resolveTargetUserId(
                args = arrayOf<Any?>("pkg", null, null, 11),
                callingUid = 10209,
                targetUidParamIndex = 1,
                targetUserIdParamIndex = 3,
            ),
        )
    }

    @Test
    fun `invalid target parameters fall back to caller user`() {
        assertEquals(
            11,
            NmsPermissionHooker.resolveTargetUserId(
                args = arrayOf<Any?>("pkg", -1, null, -1),
                callingUid = 1_110_259,
                targetUidParamIndex = 1,
                targetUserIdParamIndex = 3,
            ),
        )
    }

    @Test
    fun `foreign target gets system identity even when caller is in the same user`() {
        val identity = NmsPermissionHooker.systemIdentityForTarget(
            targetPackageName = "com.example.target",
            targetUserId = 0,
            pid = 1234,
        )

        assertEquals(1_000, (identity!! ushr 32).toInt())
        assertEquals(1234, identity.toInt())
    }

    @Test
    fun `xmsf target keeps the calling identity`() {
        assertNull(
            NmsPermissionHooker.systemIdentityForTarget(
                targetPackageName = "com.xiaomi.xmsf",
                targetUserId = 0,
                pid = 1234,
            ),
        )
    }

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
