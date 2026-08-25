package io.github.magisk317.mipush.runtime.store.kmp

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RuntimeStoreValueTypesTest {
    @Test
    fun `event identifiers preserve persisted values`() {
        assertEquals(0, EventRowType.SendMessage)
        assertEquals(2, EventRowType.Registration)
        assertEquals(20, EventRowType.UnRegistration)
        assertEquals(21, EventRowType.RegistrationResult)
        assertEquals(0, EventRowResultType.OK)
        assertEquals(2, EventRowResultType.DENY_USER)
    }

    @Test
    fun `registered application identifiers preserve persisted values`() {
        assertEquals(0, RegisteredAppType.ASK)
        assertEquals(2, RegisteredAppType.ALLOW)
        assertEquals(-1, RegisteredAppType.ALLOW_ONCE)
        assertEquals(0, RegisteredAppRegisteredType.NotRegistered)
        assertEquals(1, RegisteredAppRegisteredType.Registered)
        assertEquals(2, RegisteredAppRegisteredType.Unregistered)
    }
}
