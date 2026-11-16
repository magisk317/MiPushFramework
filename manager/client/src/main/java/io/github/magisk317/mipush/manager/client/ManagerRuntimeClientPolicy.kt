package io.github.magisk317.mipush.manager.client

import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.manager.api.ManagerWriteRequestDto
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

    // Root authorization may use 10s, its probe 3s, and credential cleanup 8s.
    // Force registration also runs two 5s launches, refresh probes, and a 6s result wait.
    private const val REVOCATION_CALL_TIMEOUT_MS = 30_000L
    private const val FORCE_REGISTRATION_CALL_TIMEOUT_MS = 60_000L
    private const val REGISTRATION_POLICY_DENY = 3

    fun writeCallPolicy(request: ManagerWriteRequestDto): ManagerRuntimeCallPolicy {
        val extendedTimeout = when {
            request.operation == ManagerProtocol.WRITE_OP_LAUNCH_TARGET_FORCE_REGISTER -> FORCE_REGISTRATION_CALL_TIMEOUT_MS
            request.operation == ManagerProtocol.WRITE_OP_UNREGISTER_APPLICATION -> REVOCATION_CALL_TIMEOUT_MS
            request.operation == ManagerProtocol.WRITE_OP_SET_REGISTRATION_POLICY &&
                request.intArgument == REGISTRATION_POLICY_DENY -> REVOCATION_CALL_TIMEOUT_MS
            else -> null
        }
        return ManagerRuntimeCallPolicy(
            timeoutMillis = extendedTimeout ?: USE_CLIENT_CALL_TIMEOUT_MILLIS,
            // A slow user root prompt is local to this operation, not a broken Binder session.
            releaseSessionOnTimeout = extendedTimeout == null,
        )
    }

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
