package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertFalse
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
}
