package io.github.magisk317.mipush.runtime.core.registration

/**
 * Platform-neutral classification for registration-result events.
 *
 * Adapters retain ownership of their event-type constants and persistence enums; this policy only
 * decides whether an already-decoded result represents a successful registration.
 */
object RegistrationResultPolicy {
    fun isSuccessfulRegistrationResult(
        eventType: Int,
        registrationResultEventType: Int,
        errorCode: Long?,
    ): Boolean = eventType == registrationResultEventType && errorCode == 0L
}
