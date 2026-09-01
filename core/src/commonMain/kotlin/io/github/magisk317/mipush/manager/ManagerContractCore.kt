package io.github.magisk317.mipush.manager

/** Primitive protocol inputs and limits shared by Android manager facades. */
data class ManagerHandshakeInput(
    val protocolMajor: Int,
    val protocolMinor: Int,
    val runtimeVersionName: String,
    val runtimeVersionCode: Long,
    val supportedCapabilities: List<String>,
    val maxPageSize: Int,
    val maxPayloadBytes: Int,
    val compatibilityReason: String?,
)

data class ManagerHandshakeLimits(
    val maxRuntimeVersionNameLength: Int,
    val maxCompatibilityReasonLength: Int,
    val maxCapabilityCount: Int,
    val maxCapabilityLength: Int,
    val maxNegotiatedPageSize: Int,
    val maxNegotiatedPayloadBytes: Int,
)

enum class ManagerNegotiationStatus { COMPATIBLE, INVALID_VERSION, MAJOR_MISMATCH }

data class ManagerNegotiationResult(
    val status: ManagerNegotiationStatus,
    val negotiatedMinor: Int?,
)

object ManagerContractCore {
    fun evaluateCompatibility(clientMajor: Int, clientMinor: Int, runtimeMajor: Int, runtimeMinor: Int) =
        when {
            clientMajor < 0 || clientMinor < 0 || runtimeMajor < 0 || runtimeMinor < 0 ->
                ManagerNegotiationResult(ManagerNegotiationStatus.INVALID_VERSION, null)
            clientMajor != runtimeMajor ->
                ManagerNegotiationResult(ManagerNegotiationStatus.MAJOR_MISMATCH, null)
            else -> ManagerNegotiationResult(ManagerNegotiationStatus.COMPATIBLE, minOf(clientMinor, runtimeMinor))
        }

    fun recognizedCapabilities(runtimeCapabilities: Iterable<String>, knownCapabilities: Set<String>): Set<String> =
        runtimeCapabilities.filterTo(linkedSetOf()) { it in knownCapabilities }

    fun validateHandshake(input: ManagerHandshakeInput, limits: ManagerHandshakeLimits): String? = when {
        input.protocolMajor < 0 || input.protocolMinor < 0 -> "invalid_protocol_version"
        input.runtimeVersionCode < 0L -> "invalid_runtime_version_code"
        input.runtimeVersionName.length > limits.maxRuntimeVersionNameLength -> "runtime_version_name_too_long"
        input.compatibilityReason?.let { it.length > limits.maxCompatibilityReasonLength ||
            it.any { char -> !char.isLetterOrDigit() && char != '_' && char != '-' && char != '.' } } == true ->
            "invalid_compatibility_reason"
        input.supportedCapabilities.size > limits.maxCapabilityCount -> "too_many_capabilities"
        input.supportedCapabilities.any { it.isBlank() || it.length > limits.maxCapabilityLength } -> "invalid_capability"
        input.maxPageSize !in 1..limits.maxNegotiatedPageSize -> "invalid_max_page_size"
        input.maxPayloadBytes !in 1..limits.maxNegotiatedPayloadBytes -> "invalid_max_payload_bytes"
        else -> null
    }

}
