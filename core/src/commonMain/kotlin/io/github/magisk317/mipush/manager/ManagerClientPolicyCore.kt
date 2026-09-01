package io.github.magisk317.mipush.manager

data class ManagerHandshakeAvailabilityDecision(
    val available: Boolean,
    val reason: String? = null,
    val warning: String? = null,
)

/** Platform-neutral manager-client availability and reconnect policy. */
object ManagerClientPolicyCore {
    const val DEFAULT_MAX_RECONNECT_ATTEMPTS = 3

    fun classifyHandshakeSignals(
        compatible: Boolean,
        compatibilityReason: String?,
        validationReason: String?,
        handshakeWarning: String?,
    ): ManagerHandshakeAvailabilityDecision = if (validationReason != null || !compatible) {
        ManagerHandshakeAvailabilityDecision(
            available = false,
            // Incompatible peers must not control user-visible failure text.
            reason = validationReason ?: compatibilityReason ?: "protocol_incompatible",
        )
    } else {
        ManagerHandshakeAvailabilityDecision(
            available = true,
            warning = handshakeWarning,
        )
    }

    fun reconnectDelayMillis(attempt: Int): Long {
        if (attempt <= 0) return INITIAL_RECONNECT_DELAY_MS
        val multiplier = 1L shl attempt.coerceAtMost(5)
        return (INITIAL_RECONNECT_DELAY_MS * multiplier).coerceAtMost(MAX_RECONNECT_DELAY_MS)
    }

    private const val INITIAL_RECONNECT_DELAY_MS = 500L
    private const val MAX_RECONNECT_DELAY_MS = 10_000L
}
