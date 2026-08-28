package io.github.magisk317.mipush.runtime.store.kmp

import io.github.magisk317.mipush.runtime.core.registration.RegistrationResultPolicy

/**
 * Persistence compatibility facade for registration-result events.
 *
 * The platform-neutral result classification lives in [RegistrationResultPolicy]. This facade
 * retains store-owned event constants and persisted enum mapping for existing callers.
 */
object RuntimeRegistrationStatePolicy {
    fun resolveRegisteredType(
        eventType: Int,
        errorCode: Long?,
    ): Int =
        if (
            RegistrationResultPolicy.isSuccessfulRegistrationResult(
                eventType = eventType,
                registrationResultEventType = EventRowType.RegistrationResult,
                errorCode = errorCode,
            )
        ) {
            RegisteredAppRegisteredType.Registered
        } else {
            RegisteredAppRegisteredType.Unregistered
        }
}
