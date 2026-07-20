package io.github.magisk317.mipush.manager.api

object ManagerProtocol {
    const val MAJOR = 1
    const val MINOR = 0

    const val RUNTIME_PACKAGE = "com.xiaomi.xmsf"
    const val MANAGER_PACKAGE = "io.github.magisk317.mipush"
    const val SERVICE_ACTION = "io.github.magisk317.mipush.action.BIND_MANAGER_RUNTIME"
    const val SERVICE_PERMISSION = "com.xiaomi.xmsf.permission.BIND_MANAGER_RUNTIME"
    const val RUNTIME_SERVICE_CLASS = "io.github.magisk317.mipush.manager.runtime.ManagerRuntimeService"

    const val CAPABILITY_CONNECTION_SNAPSHOT = "connection_snapshot"
    const val CONNECTION_SNAPSHOT_SCHEMA_VERSION = 1
    const val DEFAULT_MAX_PAGE_SIZE = 100
    const val DEFAULT_MAX_PAYLOAD_BYTES = 512 * 1024
    const val MAX_CAPABILITY_COUNT = 64
    const val MAX_CAPABILITY_LENGTH = 128
    const val MAX_RUNTIME_VERSION_NAME_LENGTH = 128
    const val MAX_WIRE_STRING_LENGTH = 4_096
    const val MAX_NEGOTIATED_PAGE_SIZE = 1_000
    const val MAX_NEGOTIATED_PAYLOAD_BYTES = DEFAULT_MAX_PAYLOAD_BYTES

    const val CONNECTION_STATE_UNKNOWN = "Unknown"
    const val CONNECTION_STATE_IDLE = "Idle"
    const val CONNECTION_STATE_CONNECTING = "Connecting"
    const val CONNECTION_STATE_CONNECTED = "Connected"
    const val CONNECTION_STATE_DISCONNECTING = "Disconnecting"
    const val CONNECTION_STATE_DISCONNECTED = "Disconnected"

    val KNOWN_CAPABILITIES: Set<String> = setOf(CAPABILITY_CONNECTION_SNAPSHOT)

    fun evaluateCompatibility(
        clientMajor: Int,
        clientMinor: Int,
        runtimeMajor: Int = MAJOR,
        runtimeMinor: Int = MINOR,
    ): Compatibility {
        if (clientMajor < 0 || clientMinor < 0 || runtimeMajor < 0 || runtimeMinor < 0) {
            return Compatibility(
                status = CompatibilityStatus.INVALID_VERSION,
                negotiatedMinor = null,
            )
        }
        if (clientMajor != runtimeMajor) {
            return Compatibility(
                status = CompatibilityStatus.MAJOR_MISMATCH,
                negotiatedMinor = null,
            )
        }
        return Compatibility(
            status = CompatibilityStatus.COMPATIBLE,
            negotiatedMinor = minOf(clientMinor, runtimeMinor),
        )
    }

    fun recognizedCapabilities(runtimeCapabilities: Iterable<String>): Set<String> =
        runtimeCapabilities.filterTo(linkedSetOf()) { it in KNOWN_CAPABILITIES }

    fun validateHandshake(handshake: ManagerHandshake): String? = when {
        handshake.protocolMajor < 0 || handshake.protocolMinor < 0 -> "invalid_protocol_version"
        handshake.runtimeVersionCode < 0L -> "invalid_runtime_version_code"
        handshake.runtimeVersionName.length > MAX_RUNTIME_VERSION_NAME_LENGTH -> "runtime_version_name_too_long"
        handshake.supportedCapabilities.size > MAX_CAPABILITY_COUNT -> "too_many_capabilities"
        handshake.supportedCapabilities.any { it.isBlank() || it.length > MAX_CAPABILITY_LENGTH } ->
            "invalid_capability"
        handshake.maxPageSize !in 1..MAX_NEGOTIATED_PAGE_SIZE -> "invalid_max_page_size"
        handshake.maxPayloadBytes !in 1..MAX_NEGOTIATED_PAYLOAD_BYTES -> "invalid_max_payload_bytes"
        else -> null
    }

    fun validateConnectionSnapshot(snapshot: ManagerConnectionSnapshotDto): String? = when {
        snapshot.schemaVersion < 1 -> "invalid_connection_snapshot_schema"
        snapshot.connectionState.length > MAX_WIRE_STRING_LENGTH -> "connection_state_too_long"
        snapshot.serverHost?.length?.let { it > MAX_WIRE_STRING_LENGTH } == true -> "server_host_too_long"
        snapshot.serverIp?.length?.let { it > MAX_WIRE_STRING_LENGTH } == true -> "server_ip_too_long"
        else -> null
    }

    data class Compatibility(
        val status: CompatibilityStatus,
        val negotiatedMinor: Int?,
    ) {
        val isCompatible: Boolean
            get() = status == CompatibilityStatus.COMPATIBLE

        val reason: String?
            get() = status.reason
    }

    enum class CompatibilityStatus(val reason: String?) {
        COMPATIBLE(null),
        INVALID_VERSION("invalid_protocol_version"),
        MAJOR_MISMATCH("protocol_major_mismatch"),
    }
}
