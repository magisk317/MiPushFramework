package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.PushConstants
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RegistrationIntentDeduperTest {

    @BeforeEach
    fun setUp() {
        RegistrationIntentDeduper.reset()
    }

    @Test
    fun `drops duplicate register app for same package within window`() {
        assertFalse(RegistrationIntentDeduper.shouldDropRegister("facade", "com.example.app", nowMs = 1_000L))
        assertTrue(RegistrationIntentDeduper.shouldDropRegister("facade", "com.example.app", nowMs = 1_500L))
    }

    @Test
    fun `allows same package after dedup window`() {
        assertFalse(RegistrationIntentDeduper.shouldDropRegister("facade", "com.example.app", nowMs = 1_000L))
        assertFalse(
            RegistrationIntentDeduper.shouldDropRegister(
                "facade",
                "com.example.app",
                nowMs = 1_000L + RegistrationIntentDeduper.DEDUP_WINDOW_MS
            )
        )
    }

    @Test
    fun `keeps scopes independent`() {
        assertFalse(RegistrationIntentDeduper.shouldDropRegister("facade", "com.example.app", nowMs = 1_000L))
        assertFalse(RegistrationIntentDeduper.shouldDropRegister("recorder", "com.example.app", nowMs = 1_500L))
        assertTrue(RegistrationIntentDeduper.shouldDropRegister("facade", "com.example.app", nowMs = 1_500L))
        assertTrue(RegistrationIntentDeduper.shouldDropRegister("recorder", "com.example.app", nowMs = 1_600L))
    }

    @Test
    fun `ignores non register intents`() {
        assertFalse(
            RegistrationIntentDeduper.shouldDrop(
                scope = "facade",
                action = PushConstants.MIPUSH_ACTION_SEND_MESSAGE,
                packageName = "com.example.app",
                nowMs = 1_000L
            )
        )
        assertFalse(
            RegistrationIntentDeduper.shouldDrop(
                scope = "facade",
                action = PushConstants.MIPUSH_ACTION_SEND_MESSAGE,
                packageName = "com.example.app",
                nowMs = 1_500L
            )
        )
    }

    @Test
    fun `resolves package from register extras first`() {
        assertEquals(
            "com.example.primary",
            RegistrationIntentDeduper.packageName(
                appPackage = "com.example.primary",
                extraPackage = "com.example.secondary",
                intentPackage = "com.example.tertiary"
            )
        )
    }

    @Test
    fun `falls back to extra package and intent package`() {
        assertEquals(
            "com.example.secondary",
            RegistrationIntentDeduper.packageName(
                appPackage = "",
                extraPackage = "com.example.secondary",
                intentPackage = "com.example.tertiary"
            )
        )
        assertEquals(
            "com.example.tertiary",
            RegistrationIntentDeduper.packageName(
                appPackage = null,
                extraPackage = " ",
                intentPackage = "com.example.tertiary"
            )
        )
    }
}
