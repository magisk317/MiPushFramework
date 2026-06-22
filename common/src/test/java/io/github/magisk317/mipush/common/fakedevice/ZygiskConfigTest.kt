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
    fun `parse drops system and Xiaomi family packages`() {
        val config = ZygiskConfig.parse(
            """
            android
            com.android.settings
            com.miui.securitycenter
            com.xiaomi.smarthome
            com.mipay.wallet
            com.example.app
            """.trimIndent(),
        )

        assertEquals(setOf("com.example.app"), config.enabledPackages())
        assertFalse(config.isEnabledForPackage("com.xiaomi.smarthome"))
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

        assertEquals("com.example.one\ncom.example.two\n", config.toFileContent())
    }
}
