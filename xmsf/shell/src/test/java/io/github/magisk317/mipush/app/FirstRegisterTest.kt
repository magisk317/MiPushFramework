package io.github.magisk317.mipush.app

import android.content.Context
import io.github.magisk317.mipush.runtime.PushRegistrationState
import io.github.magisk317.mipush.runtime.PushRuntime
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FirstRegisterTest {
    private val context = mockk<Context>(relaxed = true)

    @BeforeEach
    fun setUp() {
        PushRuntime.clearStateForTests()
        every { context.packageName } returns SERVICE_PACKAGE
    }

    @AfterEach
    fun tearDown() {
        PushRuntime.clearStateForTests()
    }

    @Test
    fun `registered service does not request framework registration again`() {
        var requested = false
        var retryScheduled = false

        FirstRegister(
            context = context,
            isRegistered = { true },
            requestRegistration = { _, _ ->
                requested = true
                true
            },
            scheduleRetry = { _, _ ->
                retryScheduled = true
            },
        ).run()

        assertFalse(requested)
        assertFalse(retryScheduled)
        assertEquals(
            PushRegistrationState.Registered,
            PushRuntime.snapshot().lastRegistrationState,
        )
        assertEquals(SERVICE_PACKAGE, PushRuntime.snapshot().lastRegistrationPackage)
    }

    @Test
    fun `unregistered service requests registration and schedules retry when still unregistered`() {
        var requested = false
        var retryScheduled = false

        FirstRegister(
            context = context,
            isRegistered = { false },
            requestRegistration = { source, reason ->
                requested = source == "FirstRegister.run" && reason == "initial_register"
                true
            },
            scheduleRetry = { _, retry ->
                retryScheduled = retry == 0
            },
        ).run()

        assertTrue(requested)
        assertTrue(retryScheduled)
    }

    private companion object {
        const val SERVICE_PACKAGE = "com.xiaomi.xmsf"
    }
}
