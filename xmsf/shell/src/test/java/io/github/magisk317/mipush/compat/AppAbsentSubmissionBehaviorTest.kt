package io.github.magisk317.mipush.compat

import android.content.Context
import android.content.SharedPreferences
import io.github.magisk317.mipush.push.bridge.DefaultPushShellBridge
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class AppAbsentSubmissionBehaviorTest {
    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `failed durable submission is not reported as accepted or sent`() {
        val context = mockk<Context>()
        val prefs = mockk<SharedPreferences>()
        val editor = mockk<SharedPreferences.Editor>()
        every { context.getSharedPreferences("pref_pending_app_absent", 0) } returns prefs
        every { prefs.edit() } returns editor
        every { editor.putString("app.pkg", "app-id") } returns editor
        every { editor.commit() } returns false
        mockkStatic(XMPushServiceLifecycleBridge::class)

        assertFalse(DefaultPushShellBridge.reportAppAbsent(context, "app.pkg", "app-id", "test"))

        verify(exactly = 1) { editor.commit() }
        verify(exactly = 0) { XMPushServiceLifecycleBridge.peekService() }
    }

    @Test
    fun `pending offline revocation prevents force register from passing the fence`() {
        val context = mockk<Context>()
        val prefs = mockk<SharedPreferences>()
        every { context.getSharedPreferences("pref_pending_app_absent", 0) } returns prefs
        every { prefs.contains("app.pkg") } returns true
        mockkStatic(XMPushServiceLifecycleBridge::class)
        every { XMPushServiceLifecycleBridge.peekService() } returns null

        assertFalse(DefaultPushShellBridge.flushAppAbsentBeforeRegister(context, "app.pkg"))

        verify(exactly = 0) { prefs.edit() }
    }
}
