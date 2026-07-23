package io.github.magisk317.mipush.manager.client

import io.github.magisk317.mipush.manager.api.ManagerHandshake
import io.github.magisk317.mipush.manager.api.ManagerProtocol

internal object ManagerRuntimeClientPolicy {
    private const val INITIAL_RECONNECT_DELAY_MS = 500L
    private const val MAX_RECONNECT_DELAY_MS = 10_000L

    fun classifyHandshake(handshake: ManagerHandshake): ManagerRuntimeAvailability {
        val compatibility = ManagerProtocol.evaluateCompatibility(
            clientMajor = ManagerProtocol.MAJOR,
            clientMinor = ManagerProtocol.MINOR,
            runtimeMajor = handshake.protocolMajor,
            runtimeMinor = handshake.protocolMinor,
        )
        val validationReason = ManagerProtocol.validateHandshake(handshake)
        return if (validationReason != null || !compatibility.isCompatible) {
            ManagerRuntimeAvailability.Incompatible(
                handshake = handshake,
                // Do not surface arbitrary text supplied by an incompatible runtime.
                reason = validationReason ?: compatibility.reason ?: "protocol_incompatible",
            )
        } else {
            ManagerRuntimeAvailability.Available(
                handshake = handshake,
                warning = handshake.compatibilityReason,
            )
        }
    }

    fun reconnectDelayMillis(attempt: Int): Long {
        if (attempt <= 0) return INITIAL_RECONNECT_DELAY_MS
        val multiplier = 1L shl attempt.coerceAtMost(5)
        return (INITIAL_RECONNECT_DELAY_MS * multiplier).coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }
}
