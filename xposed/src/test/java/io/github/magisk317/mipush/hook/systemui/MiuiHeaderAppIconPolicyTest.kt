package io.github.magisk317.mipush.hook.systemui

import net.jqwik.api.ForAll
import net.jqwik.api.Property
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiuiHeaderAppIconPolicyTest {
    @Test
    fun `replaces only xspace fallback identities with a resolved icon`() {
        assertTrue(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 999,
                targetPackage = "com.example.app",
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = true,
            )
        )

        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(0, "com.example.app", "com.xiaomi.xmsf", true)
        )
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "", "com.xiaomi.xmsf", true))
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "com.xiaomi.xmsf", "com.xiaomi.xmsf", true))
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "com.example.app", "com.xiaomi.xmsf", false))
        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 999,
                targetPackage = "com.example.app",
                postingPackage = "com.example.app",
                hasReplacementIcon = true,
            )
        )
    }

    @Test
    fun `replaces mock replay receipt header icon outside xspace`() {
        assertTrue(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = "com.example.app",
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = true,
                isMockReplayReceipt = true,
            )
        )

        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = "com.xiaomi.xmsf",
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = true,
                isMockReplayReceipt = true,
            )
        )
        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = "com.example.app",
                postingPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = false,
                isMockReplayReceipt = true,
            )
        )
    }

    @Test
    fun `only exact target package marker authorizes passed third-party small icon`() {
        assertTrue(
            MiuiHeaderAppIconPolicy.isPassedThirdPartySmallIcon(
                "THIRD_PARTY_PACK(com.example.target)",
                "com.example.target",
            ),
        )
        assertFalse(
            MiuiHeaderAppIconPolicy.isPassedThirdPartySmallIcon(
                "THIRD_PARTY_PACK(com.example.other)",
                "com.example.target",
            ),
        )
        assertFalse(
            MiuiHeaderAppIconPolicy.isPassedThirdPartySmallIcon(null, "com.example.target"),
        )
    }

    @Property(tries = 40)
    fun `header selector is strictly third-party then app then unavailable`(
        @ForAll hasPassedThirdPartySmallIcon: Boolean,
        @ForAll hasTargetAppIcon: Boolean,
    ) {
        val expected = when {
            hasPassedThirdPartySmallIcon -> MiuiHeaderAppIconSource.THIRD_PARTY_PACK
            hasTargetAppIcon -> MiuiHeaderAppIconSource.APP
            else -> MiuiHeaderAppIconSource.UNAVAILABLE
        }
        assertEquals(
            expected,
            MiuiHeaderAppIconPolicy.selectReplacementSource(
                hasPassedThirdPartySmallIcon = hasPassedThirdPartySmallIcon,
                hasTargetAppIcon = hasTargetAppIcon,
            ),
        )
    }

    @Test
    fun `header selector never treats large or original small icon as third-party`() {
        assertEquals(
            MiuiHeaderAppIconSource.APP,
            MiuiHeaderAppIconPolicy.selectReplacementSource(
                hasPassedThirdPartySmallIcon = false,
                hasTargetAppIcon = true,
            ),
        )
        assertEquals(
            MiuiHeaderAppIconSource.UNAVAILABLE,
            MiuiHeaderAppIconPolicy.selectReplacementSource(
                hasPassedThirdPartySmallIcon = false,
                hasTargetAppIcon = false,
            ),
        )
    }
}
