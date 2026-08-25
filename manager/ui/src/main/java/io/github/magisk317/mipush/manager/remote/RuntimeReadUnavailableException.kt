package io.github.magisk317.mipush.manager.remote

/**
 * Thrown by remote gateways when a Binder read is unavailable (binding, timeout, disconnect, etc.).
 * Callers must not treat this as a successful empty result or cache it as legitimate empty data.
 */
class RuntimeReadUnavailableException(
    val status: String,
    val operation: String,
) : RuntimeException("ManagerRuntime $operation unavailable: $status")
