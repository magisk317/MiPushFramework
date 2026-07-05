package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiuiHeaderAppIconPolicyTest {
    @Test
    fun `replaces only xspace mipush notifications with a resolved large icon`() {
        assertTrue(MiuiHeaderAppIconPolicy.shouldReplace(999, "com.example.app", hasReplacementIcon = true))

        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(0, "com.example.app", hasReplacementIcon = true))
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "", hasReplacementIcon = true))
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "com.xiaomi.xmsf", hasReplacementIcon = true))
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "com.example.app", hasReplacementIcon = false))
    }

    @Test
    fun `replaces mock replay receipt header icon outside xspace`() {
        assertTrue(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = "com.example.app",
                hasReplacementIcon = true,
                isMockReplayReceipt = true,
            )
        )

        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = "com.xiaomi.xmsf",
                hasReplacementIcon = true,
                isMockReplayReceipt = true,
            )
        )
        assertFalse(
            MiuiHeaderAppIconPolicy.shouldReplace(
                userId = 0,
                targetPackage = "com.example.app",
                hasReplacementIcon = false,
                isMockReplayReceipt = true,
            )
        )
    }

    @Test
    fun `mock replay header prefers colored target app icon before small icon fallback`() {
        assertEquals(
            MiuiHeaderAppIconSource.TARGET_APP,
            MiuiHeaderAppIconPolicy.mockReplayReplacementSource(
                hasTargetAppIcon = true,
                hasLargeIcon = true,
                hasSmallIcon = true,
            ),
        )
        assertEquals(
            MiuiHeaderAppIconSource.LARGE_ICON,
            MiuiHeaderAppIconPolicy.mockReplayReplacementSource(
                hasTargetAppIcon = false,
                hasLargeIcon = true,
                hasSmallIcon = true,
            ),
        )
        assertEquals(
            MiuiHeaderAppIconSource.SMALL_ICON,
            MiuiHeaderAppIconPolicy.mockReplayReplacementSource(
                hasTargetAppIcon = false,
                hasLargeIcon = false,
                hasSmallIcon = true,
            ),
        )
        assertNull(
            MiuiHeaderAppIconPolicy.mockReplayReplacementSource(
                hasTargetAppIcon = false,
                hasLargeIcon = false,
                hasSmallIcon = false,
            ),
        )
    }
}
