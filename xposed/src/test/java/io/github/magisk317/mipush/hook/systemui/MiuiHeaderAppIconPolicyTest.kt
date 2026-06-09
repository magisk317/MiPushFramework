package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiuiHeaderAppIconPolicyTest {
    @Test
    fun `replaces only xspace mipush notifications with a resolved large icon`() {
        assertTrue(MiuiHeaderAppIconPolicy.shouldReplace(999, "com.example.app", hasLargeIcon = true))

        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(0, "com.example.app", hasLargeIcon = true))
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "", hasLargeIcon = true))
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "com.xiaomi.xmsf", hasLargeIcon = true))
        assertFalse(MiuiHeaderAppIconPolicy.shouldReplace(999, "com.example.app", hasLargeIcon = false))
    }
}
