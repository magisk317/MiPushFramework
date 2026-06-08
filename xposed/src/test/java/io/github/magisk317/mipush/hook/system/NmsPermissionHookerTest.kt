package io.github.magisk317.mipush.hook.system

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NmsPermissionHookerTest {
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
}
