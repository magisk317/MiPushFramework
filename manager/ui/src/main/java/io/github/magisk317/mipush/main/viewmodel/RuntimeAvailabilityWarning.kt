package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability

private val REQUIRED_MANAGER_CAPABILITIES = setOf(
    ManagerProtocol.CAPABILITY_EVENT_LIST,
    ManagerProtocol.CAPABILITY_RUNTIME_PREFERENCES,
    ManagerProtocol.CAPABILITY_RUNTIME_ENVIRONMENT,
)

/**
 * Returns true only after the connection attempt has established that no usable
 * MiPushFramework runtime is available. Binding/initial disconnected states are
 * deliberately non-blocking so the dialog does not flash during startup.
 */
internal fun ManagerRuntimeAvailability.requiresRuntimeWarning(): Boolean = when (this) {
    ManagerRuntimeAvailability.RuntimeMissing -> true

    is ManagerRuntimeAvailability.Available ->
        !REQUIRED_MANAGER_CAPABILITIES.all { it in handshake.supportedCapabilities }

    ManagerRuntimeAvailability.Disconnected,
    ManagerRuntimeAvailability.Binding,
    ManagerRuntimeAvailability.PermissionDenied,
    ManagerRuntimeAvailability.TimedOut,
    is ManagerRuntimeAvailability.Incompatible,
    is ManagerRuntimeAvailability.TemporarilyDisconnected,
    is ManagerRuntimeAvailability.Failed,
    -> false
}
