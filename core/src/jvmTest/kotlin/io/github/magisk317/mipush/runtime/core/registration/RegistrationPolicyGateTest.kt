package io.github.magisk317.mipush.runtime.core.registration

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RegistrationPolicyGateTest {
    @Test
    fun `blocked always denies including an explicit allow`() {
        for (type in listOf(-1, 0, 2, 3, 999)) {
            assertEquals(RegistrationPolicyDecision.Deny, RegistrationPolicyGate.decide(type, true))
        }
    }

    @Test
    fun `wire modes select allow ask and deny without restoring once UI`() {
        assertEquals(RegistrationPolicyDecision.Allow, RegistrationPolicyGate.decide(2, false))
        assertEquals(RegistrationPolicyDecision.Allow, RegistrationPolicyGate.decide(-1, false))
        assertEquals(RegistrationPolicyDecision.Ask, RegistrationPolicyGate.decide(0, false))
        assertEquals(RegistrationPolicyDecision.Deny, RegistrationPolicyGate.decide(3, false))
        assertEquals(RegistrationPolicyDecision.Deny, RegistrationPolicyGate.decide(999, false))
    }
}
