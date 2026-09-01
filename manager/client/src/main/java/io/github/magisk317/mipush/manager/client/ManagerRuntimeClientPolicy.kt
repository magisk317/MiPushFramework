package io.github.magisk317.mipush.manager.client

import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.ManagerClientPolicyCore
import io.github.magisk317.mipush.manager.ManagerHandshakeAvailabilityDecision

internal data class ManagerRuntimeCallPolicy(
    val timeoutMillis: Long?,
    val releaseSessionOnTimeout: Boolean,
)

internal typealias HandshakeAvailabilityDecision = ManagerHandshakeAvailabilityDecision

internal object ManagerRuntimeClientPolicy {
    internal const val DEFAULT_MAX_RECONNECT_ATTEMPTS = ManagerClientPolicyCore.DEFAULT_MAX_RECONNECT_ATTEMPTS

    fun eventPageCallPolicy(timeoutMillis: Long?): ManagerRuntimeCallPolicy =
        ManagerRuntimeCallPolicy(
            timeoutMillis = timeoutMillis,
            releaseSessionOnTimeout = false,
        )

    fun logExportCallPolicy(): ManagerRuntimeCallPolicy =
        ManagerRuntimeCallPolicy(
            timeoutMillis = ManagerRuntimeClient.LOG_EXPORT_CALL_TIMEOUT_MS,
            releaseSessionOnTimeout = true,
        )

    fun classifyHandshake(handshake: ManagerHandshake): ManagerRuntimeAvailability {
        val compatibility = ManagerProtocol.evaluateCompatibility(
            clientMajor = ManagerProtocol.MAJOR,
            clientMinor = ManagerProtocol.MINOR,
            runtimeMajor = handshake.protocolMajor,
            runtimeMinor = handshake.protocolMinor,
        )
        val decision = classifyHandshakeSignals(
            compatible = compatibility.isCompatible,
            compatibilityReason = compatibility.reason,
            validationReason = ManagerProtocol.validateHandshake(handshake),
            handshakeWarning = handshake.compatibilityReason,
        )
        return if (decision.available) {
            ManagerRuntimeAvailability.Available(
                handshake = handshake,
                warning = decision.warning,
            )
        } else {
            ManagerRuntimeAvailability.Incompatible(
                handshake = handshake,
                reason = decision.reason ?: "protocol_incompatible",
            )
        }
    }

    fun classifyHandshakeSignals(
        compatible: Boolean,
        compatibilityReason: String?,
        validationReason: String?,
        handshakeWarning: String?,
    ): HandshakeAvailabilityDecision {
        return ManagerClientPolicyCore.classifyHandshakeSignals(
            compatible = compatible,
            compatibilityReason = compatibilityReason,
            validationReason = validationReason,
            handshakeWarning = handshakeWarning,
        )
    }

    fun reconnectDelayMillis(attempt: Int): Long = ManagerClientPolicyCore.reconnectDelayMillis(attempt)
}
