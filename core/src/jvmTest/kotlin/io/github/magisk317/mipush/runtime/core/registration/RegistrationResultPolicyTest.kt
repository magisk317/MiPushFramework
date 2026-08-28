package io.github.magisk317.mipush.runtime.core.registration

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationResultPolicyTest {

    @Test
    fun `zero error registration result is successful`() {
        assertTrue(
            RegistrationResultPolicy.isSuccessfulRegistrationResult(
                eventType = 1,
                registrationResultEventType = 1,
                errorCode = 0L,
            ),
        )
    }

    @Test
    fun `failed or missing registration result is unsuccessful`() {
        assertFalse(
            RegistrationResultPolicy.isSuccessfulRegistrationResult(
                eventType = 1,
                registrationResultEventType = 1,
                errorCode = 401L,
            ),
        )
        assertFalse(
            RegistrationResultPolicy.isSuccessfulRegistrationResult(
                eventType = 1,
                registrationResultEventType = 1,
                errorCode = null,
            ),
        )
    }

    @Test
    fun `non registration event is unsuccessful`() {
        assertFalse(
            RegistrationResultPolicy.isSuccessfulRegistrationResult(
                eventType = 2,
                registrationResultEventType = 1,
                errorCode = 0L,
            ),
        )
    }
}
