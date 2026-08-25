package io.github.magisk317.mipush.runtime.store.kmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuntimeRegistrationStatePolicyTest {
    @Test
    fun `zero error registration result is registered`() {
        assertEquals(
            RegisteredAppRegisteredType.Registered,
            RuntimeRegistrationStatePolicy.resolveRegisteredType(
                eventType = EventRowType.RegistrationResult,
                errorCode = 0L,
            ),
        )
    }

    @Test
    fun `failed or missing registration result is unregistered`() {
        assertEquals(
            RegisteredAppRegisteredType.Unregistered,
            RuntimeRegistrationStatePolicy.resolveRegisteredType(
                eventType = EventRowType.RegistrationResult,
                errorCode = 401L,
            ),
        )
        assertEquals(
            RegisteredAppRegisteredType.Unregistered,
            RuntimeRegistrationStatePolicy.resolveRegisteredType(
                eventType = EventRowType.RegistrationResult,
                errorCode = null,
            ),
        )
    }

    @Test
    fun `non registration events are unregistered`() {
        assertEquals(
            RegisteredAppRegisteredType.Unregistered,
            RuntimeRegistrationStatePolicy.resolveRegisteredType(
                eventType = EventRowType.Registration,
                errorCode = 0L,
            ),
        )
    }
}
