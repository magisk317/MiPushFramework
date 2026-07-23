package io.github.magisk317.mipush.manager.client

import io.github.magisk317.mipush.manager.api.ManagerHandshake

sealed interface ManagerRuntimeAvailability {
    data object Disconnected : ManagerRuntimeAvailability
    data object Binding : ManagerRuntimeAvailability
    data object RuntimeMissing : ManagerRuntimeAvailability
    data object PermissionDenied : ManagerRuntimeAvailability
    data object TimedOut : ManagerRuntimeAvailability

    data class Available(
        val handshake: ManagerHandshake,
        val warning: String? = null,
    ) : ManagerRuntimeAvailability

    data class Incompatible(
        val handshake: ManagerHandshake,
        val reason: String,
    ) : ManagerRuntimeAvailability

    data class TemporarilyDisconnected(
        val reason: DisconnectReason,
    ) : ManagerRuntimeAvailability

    data class Failed(
        val reason: String,
    ) : ManagerRuntimeAvailability
}

enum class DisconnectReason {
    BIND_REJECTED,
    BINDER_DIED,
    BINDING_DIED,
    NULL_BINDING,
    SERVICE_DISCONNECTED,
    REMOTE_ERROR,
}

sealed interface ManagerRuntimeResult<out T> {
    data class Success<T>(val value: T) : ManagerRuntimeResult<T>
    data class Unsupported(val capability: String) : ManagerRuntimeResult<Nothing>
    data class Unavailable(val availability: ManagerRuntimeAvailability) : ManagerRuntimeResult<Nothing>
    data class Failed(val reason: String) : ManagerRuntimeResult<Nothing>
}
