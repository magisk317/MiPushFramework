package io.github.magisk317.mipush.control

import android.app.Application
import android.content.Context
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushControllerUtilsProcessTest {
    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `isAppMainProc prefers Application process name when available`() {
        mockkStatic(Application::class)
        every { Application.getProcessName() } returns "com.xiaomi.xmsf"

        val context = mockk<Context>(relaxed = true) {
            every { packageName } returns "com.xiaomi.xmsf"
        }

        assertTrue(PushControllerUtils.isAppMainProc(context))
    }

    @Test
    fun `isAppMainProc returns false for subprocess name`() {
        mockkStatic(Application::class)
        every { Application.getProcessName() } returns "com.xiaomi.xmsf:services"

        val context = mockk<Context>(relaxed = true) {
            every { packageName } returns "com.xiaomi.xmsf"
        }

        assertFalse(PushControllerUtils.isAppMainProc(context))
    }

    @Test
    fun `startup policy keeps a disabled service stopped`() {
        assertFalse(PushControllerUtils.shouldStartServiceFromPersistedPreference(false))
    }

    @Test
    fun `startup policy restores an enabled service`() {
        assertTrue(PushControllerUtils.shouldStartServiceFromPersistedPreference(true))
    }
}
