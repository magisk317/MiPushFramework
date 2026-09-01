package io.github.magisk317.mipush.manager.api

import io.github.magisk317.mipush.manager.ManagerContractCore
import io.github.magisk317.mipush.manager.ManagerHandshakeInput
import io.github.magisk317.mipush.manager.ManagerHandshakeLimits

/** Platform-neutral handshake fields used by the contract facade. */
internal typealias HandshakeValidationInput = ManagerHandshakeInput

/** Platform-neutral bounds for validating [HandshakeValidationInput]. */
internal typealias HandshakeValidationLimits = ManagerHandshakeLimits

internal enum class NegotiationStatus {
    COMPATIBLE,
    INVALID_VERSION,
    MAJOR_MISMATCH,
}

/** Platform-neutral result; [ManagerProtocol] maps it back to its public nested types. */
internal data class NegotiationResult(
    val status: NegotiationStatus,
    val negotiatedMinor: Int?,
)

/** Internal protocol negotiation domain; [ManagerProtocol] preserves the public facade. */
internal object ManagerProtocolNegotiationSupport {
    fun evaluateCompatibility(
        clientMajor: Int,
        clientMinor: Int,
        runtimeMajor: Int,
        runtimeMinor: Int,
    ): NegotiationResult {
        val result = ManagerContractCore.evaluateCompatibility(clientMajor, clientMinor, runtimeMajor, runtimeMinor)
        return NegotiationResult(NegotiationStatus.valueOf(result.status.name), result.negotiatedMinor)
    }

    fun recognizedCapabilities(
        runtimeCapabilities: Iterable<String>,
        knownCapabilities: Set<String>,
    ): Set<String> = ManagerContractCore.recognizedCapabilities(runtimeCapabilities, knownCapabilities)

    fun validateHandshake(
        input: HandshakeValidationInput,
        limits: HandshakeValidationLimits,
    ): String? = ManagerContractCore.validateHandshake(
        input,
        limits,
    )
}
