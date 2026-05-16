package io.github.magisk317.mipush.hook.system

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class HookSystemServiceTest {
    @Test
    fun `visibility allows explicit profile caller to see xmsf`() {
        val decision = HookSystemService.shouldAllowMiPushVisibility(
            callingPackages = listOf("com.ss.android.ugc.aweme"),
            targetPackageName = "com.xiaomi.xmsf",
        )

        assertTrue(decision.allow)
        assertEquals("xmsf_for_profile", decision.reason)
        assertEquals("com.ss.android.ugc.aweme", decision.caller)
    }

    @Test
    fun `visibility allows explicit profile caller to see self`() {
        val decision = HookSystemService.shouldAllowMiPushVisibility(
            callingPackages = listOf("com.jingdong.app.mall"),
            targetPackageName = "com.jingdong.app.mall",
        )

        assertTrue(decision.allow)
        assertEquals("self_for_profile", decision.reason)
    }

    @Test
    fun `visibility rejects non profile callers and unrelated targets`() {
        assertFalse(
            HookSystemService.shouldAllowMiPushVisibility(
                callingPackages = listOf("com.example.auto"),
                targetPackageName = "com.xiaomi.xmsf",
            ).allow,
        )
        assertFalse(
            HookSystemService.shouldAllowMiPushVisibility(
                callingPackages = listOf("com.ss.android.ugc.aweme"),
                targetPackageName = "com.other.app",
            ).allow,
        )
        assertFalse(
            HookSystemService.shouldAllowMiPushVisibility(
                callingPackages = listOf("android", "com.xiaomi.account"),
                targetPackageName = "com.xiaomi.xmsf",
            ).allow,
        )
    }

    @Test
    fun `calling packages resolve from package setting`() {
        assertEquals(
            listOf("com.ss.android.ugc.aweme"),
            HookSystemService.callingPackagesFromSetting(FakePackageSetting("com.ss.android.ugc.aweme")),
        )
    }

    @Test
    fun `calling packages resolve from shared user setting`() {
        assertEquals(
            listOf("com.ss.android.ugc.aweme", "com.jingdong.app.mall"),
            HookSystemService.callingPackagesFromSetting(
                FakeSharedUserSetting(
                    listOf(
                        FakePackageSetting("com.ss.android.ugc.aweme"),
                        FakePackageSetting("com.jingdong.app.mall"),
                    )
                )
            ),
        )
    }

    private class FakePackageSetting(private val packageName: String) {
        fun getPackageName(): String = packageName
    }

    private class FakeSharedUserSetting(private val packageStates: List<FakePackageSetting>) {
        fun getPackageStates(): List<FakePackageSetting> = packageStates
    }
}
