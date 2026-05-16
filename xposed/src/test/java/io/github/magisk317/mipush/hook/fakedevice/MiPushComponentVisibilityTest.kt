package io.github.magisk317.mipush.hook.fakedevice

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushComponentVisibilityTest {
    @Test
    fun `receiver query matches only mipush actions targeting self or implicit package`() {
        assertTrue(
            MiPushComponentVisibility.isMiPushReceiverQuery(
                action = "com.xiaomi.mipush.RECEIVE_MESSAGE",
                targetPackage = null,
                ownPackage = "com.example.app",
            ),
        )
        assertTrue(
            MiPushComponentVisibility.isMiPushReceiverQuery(
                action = "com.xiaomi.mipush.miui.RECEIVE_MESSAGE",
                targetPackage = "com.example.app",
                ownPackage = "com.example.app",
            ),
        )
        assertFalse(
            MiPushComponentVisibility.isMiPushReceiverQuery(
                action = "com.xiaomi.mipush.RECEIVE_MESSAGE",
                targetPackage = "com.other.app",
                ownPackage = "com.example.app",
            ),
        )
    }

    @Test
    fun `service query allows xmsf and own click routing only`() {
        assertTrue(
            MiPushComponentVisibility.isMiPushServiceQuery(
                action = null,
                targetPackage = "com.xiaomi.xmsf",
                componentPackage = null,
                ownPackage = "com.example.app",
            ),
        )
        assertTrue(
            MiPushComponentVisibility.isMiPushServiceQuery(
                action = "com.xiaomi.mipush.miui.CLICK_MESSAGE",
                targetPackage = "com.example.app",
                componentPackage = null,
                ownPackage = "com.example.app",
            ),
        )
        assertFalse(
            MiPushComponentVisibility.isMiPushServiceQuery(
                action = "com.xiaomi.mipush.miui.CLICK_MESSAGE",
                targetPackage = "com.other.app",
                componentPackage = null,
                ownPackage = "com.example.app",
            ),
        )
    }

    @Test
    fun `activity query matches mipush actions for self xmsf or implicit targets`() {
        assertTrue(
            MiPushComponentVisibility.isMiPushActivityQuery(
                action = "com.xiaomi.mipush.RECEIVE_MESSAGE",
                targetPackage = null,
                ownPackage = "com.example.app",
            ),
        )
        assertTrue(
            MiPushComponentVisibility.isMiPushActivityQuery(
                action = "com.xiaomi.push.PING_TIMER",
                targetPackage = "com.xiaomi.xmsf",
                ownPackage = "com.example.app",
            ),
        )
        assertFalse(
            MiPushComponentVisibility.isMiPushActivityQuery(
                action = "com.xiaomi.push.PING_TIMER",
                targetPackage = "com.other.app",
                ownPackage = "com.example.app",
            ),
        )
    }

    @Test
    fun `installer query only patches xmsf or current package`() {
        assertTrue(MiPushComponentVisibility.shouldPatchInstallerQuery("com.xiaomi.xmsf", "com.example.app"))
        assertTrue(MiPushComponentVisibility.shouldPatchInstallerQuery("com.example.app", "com.example.app"))
        assertFalse(MiPushComponentVisibility.shouldPatchInstallerQuery("com.other.app", "com.example.app"))
    }

    @Test
    fun `provider query patches xmsf or all providers only`() {
        assertTrue(MiPushComponentVisibility.shouldPatchProviderQuery(null))
        assertTrue(MiPushComponentVisibility.shouldPatchProviderQuery("com.xiaomi.xmsf"))
        assertFalse(MiPushComponentVisibility.shouldPatchProviderQuery("com.other.app"))
    }
}
