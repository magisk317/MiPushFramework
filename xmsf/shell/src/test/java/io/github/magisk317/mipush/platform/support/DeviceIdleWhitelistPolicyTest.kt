package io.github.magisk317.mipush.platform.support

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceIdleWhitelistPolicyTest {
    @Test
    fun `empty target set is not a successful whitelist grant`() {
        assertFalse(DeviceIdleWhitelistPolicy.areAllWhitelisted(emptyList()) { true })
    }

    @Test
    fun `every target must be observed in the framework whitelist`() {
        assertFalse(
            DeviceIdleWhitelistPolicy.areAllWhitelisted(listOf("com.xiaomi.xmsf", "io.github.magisk317.mipush")) {
                it == "com.xiaomi.xmsf"
            },
        )
        assertTrue(
            DeviceIdleWhitelistPolicy.areAllWhitelisted(listOf("com.xiaomi.xmsf", "io.github.magisk317.mipush")) {
                it in setOf("com.xiaomi.xmsf", "io.github.magisk317.mipush")
            },
        )
    }
}
