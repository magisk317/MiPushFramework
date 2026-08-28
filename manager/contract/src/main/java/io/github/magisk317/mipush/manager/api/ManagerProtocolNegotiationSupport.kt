package io.github.magisk317.mipush.manager.api

/** Internal protocol negotiation domain; [ManagerProtocol] preserves the public facade. */
internal object ManagerProtocolNegotiationSupport {
    fun evaluateCompatibility(
        clientMajor: Int,
        clientMinor: Int,
        runtimeMajor: Int,
        runtimeMinor: Int,
    ): ManagerProtocol.Compatibility {
        if (clientMajor < 0 || clientMinor < 0 || runtimeMajor < 0 || runtimeMinor < 0) {
            return ManagerProtocol.Compatibility(ManagerProtocol.CompatibilityStatus.INVALID_VERSION, null)
        }
        if (clientMajor != runtimeMajor) {
            return ManagerProtocol.Compatibility(ManagerProtocol.CompatibilityStatus.MAJOR_MISMATCH, null)
        }
        return ManagerProtocol.Compatibility(
            status = ManagerProtocol.CompatibilityStatus.COMPATIBLE,
            negotiatedMinor = minOf(clientMinor, runtimeMinor),
        )
    }

    fun recognizedCapabilities(runtimeCapabilities: Iterable<String>): Set<String> =
        runtimeCapabilities.filterTo(linkedSetOf()) { it in ManagerProtocol.KNOWN_CAPABILITIES }

    fun validateHandshake(handshake: ManagerHandshake): String? = when {
        handshake.protocolMajor < 0 || handshake.protocolMinor < 0 -> "invalid_protocol_version"
        handshake.runtimeVersionCode < 0L -> "invalid_runtime_version_code"
        handshake.runtimeVersionName.length > ManagerProtocol.MAX_RUNTIME_VERSION_NAME_LENGTH ->
            "runtime_version_name_too_long"
        handshake.compatibilityReason?.let { reason ->
            reason.length > ManagerProtocol.MAX_COMPATIBILITY_REASON_LENGTH ||
                reason.any { !it.isLetterOrDigit() && it != '_' && it != '-' && it != '.' }
        } == true -> "invalid_compatibility_reason"
        handshake.supportedCapabilities.size > ManagerProtocol.MAX_CAPABILITY_COUNT -> "too_many_capabilities"
        handshake.supportedCapabilities.any { it.isBlank() || it.length > ManagerProtocol.MAX_CAPABILITY_LENGTH } ->
            "invalid_capability"
        handshake.maxPageSize !in 1..ManagerProtocol.MAX_NEGOTIATED_PAGE_SIZE -> "invalid_max_page_size"
        handshake.maxPayloadBytes !in 1..ManagerProtocol.MAX_NEGOTIATED_PAYLOAD_BYTES ->
            "invalid_max_payload_bytes"
        else -> null
    }
}
