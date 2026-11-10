package io.github.magisk317.mipush.compat

import android.content.Context
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.push.bridge.PushShellBridgeHolder
import io.github.magisk317.mipush.push.bridge.PushShellBridge
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class RegistrationRevocationBehaviorTest {
    @AfterEach
    fun tearDown() = unmockkAll()

    @Test
    fun `late server confirmation cannot replace a manually denied row with stale allowed state`() {
        mockkStatic(RegisteredApplicationDb::class)
        val old = row(type = 2, blocked = false)
        every { RegisteredApplicationDb.getRegisteredApplication("app.pkg") } returns row(type = 3, blocked = true)

        assertFalse(RegistrationStateStore.updateIfChanged(old, RegisteredAppRegisteredType.Registered, RegistrationStateStore.Source.SERVER_RESULT))

        verify(exactly = 0) { RegisteredApplicationDb.update(any()) }
    }

    @Test
    fun `late local probe cannot mark ASK row registered`() {
        mockkStatic(RegisteredApplicationDb::class)
        val current = row(type = 0, blocked = false)
        every { RegisteredApplicationDb.getRegisteredApplication("app.pkg") } returns current

        assertFalse(RegistrationStateStore.updateIfChanged(current, RegisteredAppRegisteredType.Registered, RegistrationStateStore.Source.LOCAL_PROBE))

        verify(exactly = 0) { RegisteredApplicationDb.update(any()) }
    }

    @Test
    fun `late registration secrets are not restored for denied or asking packages`() {
        mockkStatic(RegisteredApplicationDb::class, Utils::class)
        io.mockk.mockkObject(PushShellBridgeHolder)
        every { Utils.myUserId() } returns 0
        every { Utils.requireValidUserId(0) } returns 0
        val bridge = mockk<PushShellBridge>(relaxed = true)
        every { PushShellBridgeHolder.registration() } returns bridge
        val context = mockk<Context>()
        for (policy in listOf(0, 3)) {
            every { RegisteredApplicationDb.getRegisteredApplication("app.pkg") } returns row(policy, policy == 3)
            MiPushRuntimeBridge.persistConfirmedRegistrationState(
                context,
                "app.pkg",
                MiPushRuntimeBridge.ConfirmedRegistrationTransition(RegisteredAppRegisteredType.Registered, "app-id", "secret"),
            )
        }
        verify(exactly = 0) { bridge.rememberRegisteredPackage(any(), any(), any()) }
        verify(exactly = 0) { bridge.setRegSec(any(), any(), any()) }
    }

    private fun row(type: Int, blocked: Boolean) = RuntimeRegisteredApplicationRow(
        id = 1L,
        packageName = "app.pkg",
        userId = 0,
        type = type,
        blocked = blocked,
        notificationOnRegister = false,
        registeredType = RegisteredAppRegisteredType.Unregistered,
        appName = "App",
    )
}
