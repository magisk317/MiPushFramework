package io.github.magisk317.mipush.common.fakedevice

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZygiskConfigTest {
    @Test
    fun `parse keeps package and process entries`() {
        val config = ZygiskConfig.parse(
            """
            # comment
            com.example.app
            com.example.chat|com.example.chat:push
            """.trimIndent(),
        )

        assertTrue(config.isEnabledForPackage("com.example.app"))
        assertTrue(config.isEnabledForPackage("com.example.chat"))
        assertEquals(
            listOf(
                ZygiskConfigEntry("com.example.app"),
                ZygiskConfigEntry("com.example.chat", "com.example.chat:push"),
            ),
            config.entries,
        )
    }

    @Test
    fun `parse keeps Xiaomi family packages and drops invalid names`() {
        val config = ZygiskConfig.parse(
            """
            android
            not-a-package
            com.android.settings
            com.miui.securitycenter
            com.xiaomi.smarthome
            com.mipay.wallet
            com.example.app
            """.trimIndent(),
        )

        assertEquals(
            setOf(
                "com.android.settings",
                "com.example.app",
                "com.mipay.wallet",
                "com.miui.securitycenter",
                "com.xiaomi.smarthome",
            ),
            config.enabledPackages(),
        )
        assertTrue(config.isEnabledForPackage("com.xiaomi.smarthome"))
        assertFalse(config.isEnabledForPackage("android"))
    }

    @Test
    fun `package switch replaces process-scoped entries`() {
        val config = ZygiskConfig.parse(
            """
            com.example.app|com.example.app:push
            com.example.other
            """.trimIndent(),
        ).withPackageEnabled("com.example.app", enabled = true)

        assertEquals(
            listOf(
                ZygiskConfigEntry("com.example.app"),
                ZygiskConfigEntry("com.example.other"),
            ),
            config.entries,
        )
    }

    @Test
    fun `serialization is normalized and newline terminated`() {
        val config = ZygiskConfig.parse(
            """
            com.example.two
            com.example.one
            com.example.one
            """.trimIndent(),
        )

        assertEquals(
            "profile=miui14\nobserve=false\nauto_scan=false\ncom.example.one\ncom.example.two\n",
            config.toFileContent(),
        )
    }

    @Test
    fun `metadata and deny rules round trip`() {
        val config = ZygiskConfig.parse(
            "profile=hyperos1\nobserve=true\nauto_scan=true\ncom.example.app\n-com.example.app|com.example.app:push\n",
        )
        assertEquals("hyperos1", config.profile)
        assertTrue(config.observe)
        assertTrue(config.autoScan)
        assertFalse(config.entries.first { it.processName != null }.enabled)
        assertEquals(config, ZygiskConfig.parse(config.toFileContent()))
    }
}
