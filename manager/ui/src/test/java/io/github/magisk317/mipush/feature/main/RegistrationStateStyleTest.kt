package io.github.magisk317.mipush.feature.main

import io.github.magisk317.mipush.manager.R
import io.github.magisk317.mipush.manager.application.ManagerApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RegistrationStateStyleTest {
    private val registered = ManagerApplication(
        packageName = "test.app",
        type = ManagerApplication.Type.ALLOW,
        registeredType = ManagerApplication.RegisteredType.REGISTERED,
        existServices = true,
        lastReceiveTimeMs = 100L,
    )

    @Test
    fun `denial overrides registered and recent activity in detail and list badges`() {
        val denied = registered.copy(type = ManagerApplication.Type.DENY)

        assertEquals(R.string.app_registration_denied, RegistrationStateStyle.registrationLabelResOf(denied))
        assertEquals(RegistrationStateStyle.ErrorColor, RegistrationStateStyle.registrationColorOf(denied))
        assertEquals(R.string.app_registration_denied to RegistrationStateStyle.ErrorColor, RegistrationStateStyle.contentOf(denied))
        assertTrue(RegistrationStateStyle.isConfirmedRegistered(denied), "display must preserve the actual registration result")
    }

    @Test
    fun `hard block overrides automatic asking and pending registration`() {
        for (policy in listOf(ManagerApplication.Type.ALLOW, ManagerApplication.Type.ASK)) {
            val blocked = registered.copy(type = policy, blocked = true, registrationPending = true)
            assertEquals(R.string.app_registration_denied, RegistrationStateStyle.registrationLabelResOf(blocked))
            assertEquals(RegistrationStateStyle.ErrorColor, RegistrationStateStyle.registrationColorOf(blocked))
        }
    }

    @Test
    fun `deny takes priority over an older pending packet`() {
        val denied = registered.copy(type = ManagerApplication.Type.DENY, registrationPending = true)
        assertEquals(R.string.app_registration_denied, RegistrationStateStyle.registrationLabelResOf(denied))
    }

    @Test
    fun `real asking request overrides old registration and activity`() {
        val pending = registered.copy(type = ManagerApplication.Type.ASK, registrationPending = true)
        assertEquals(R.string.app_registration_pending, RegistrationStateStyle.registrationLabelResOf(pending))
        assertEquals(R.string.app_registration_pending to RegistrationStateStyle.YellowColor, RegistrationStateStyle.contentOf(pending))
    }

    @Test
    fun `asking without a packet does not invent pending approval`() {
        val asking = registered.copy(type = ManagerApplication.Type.ASK)
        assertEquals(R.string.app_registered, RegistrationStateStyle.registrationLabelResOf(asking))
        assertEquals(
            R.string.status_app_not_registered,
            RegistrationStateStyle.registrationLabelResOf(asking.copy(registeredType = ManagerApplication.RegisteredType.NOT_REGISTERED, lastReceiveTimeMs = 0L)),
        )
    }

    @Test
    fun `returning to automatic restores the actual result without inventing registration`() {
        for ((result, label) in listOf(
            ManagerApplication.RegisteredType.REGISTERED to R.string.app_registered,
            ManagerApplication.RegisteredType.UNREGISTERED to R.string.app_registered_error,
            ManagerApplication.RegisteredType.NOT_REGISTERED to R.string.status_app_not_registered,
        )) {
            val denied = registered.copy(type = ManagerApplication.Type.DENY, blocked = true, registeredType = result, lastReceiveTimeMs = 0L)
            val automatic = denied.copy(type = ManagerApplication.Type.ALLOW, blocked = false)
            assertEquals(label, RegistrationStateStyle.registrationLabelResOf(automatic))
            assertEquals(result, automatic.registeredType)
        }
    }

    @Test
    fun `approved packet retained during replay is not still waiting for approval`() {
        assertEquals(R.string.app_registered, RegistrationStateStyle.registrationLabelResOf(registered.copy(registrationPending = true)))
    }
}
