package io.github.magisk317.mipush.runtime.store.kmp

/**
 * Pure persisted-state classification for registration-result events.
 *
 * The Android adapter is responsible for decoding the optional Thrift result and passes only the
 * event type and error code here. Unknown, missing, and non-zero results remain unregistered.
 */
object RuntimeRegistrationStatePolicy {
    fun resolveRegisteredType(
        eventType: Int,
        errorCode: Long?,
    ): Int =
        if (eventType == EventRowType.RegistrationResult && errorCode == 0L) {
            RegisteredAppRegisteredType.Registered
        } else {
            RegisteredAppRegisteredType.Unregistered
        }
}
