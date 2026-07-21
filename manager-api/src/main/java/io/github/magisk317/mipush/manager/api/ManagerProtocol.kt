package io.github.magisk317.mipush.manager.api

object ManagerProtocol {
    const val MAJOR = 1
    const val MINOR = 1

    const val RUNTIME_PACKAGE = "com.xiaomi.xmsf"
    const val MANAGER_PACKAGE = "io.github.magisk317.mipush"
    const val SERVICE_ACTION = "io.github.magisk317.mipush.action.BIND_MANAGER_RUNTIME"
    const val SERVICE_PERMISSION = "com.xiaomi.xmsf.permission.BIND_MANAGER_RUNTIME"
    const val RUNTIME_SERVICE_CLASS = "io.github.magisk317.mipush.manager.runtime.ManagerRuntimeService"

    const val CAPABILITY_CONNECTION_SNAPSHOT = "connection_snapshot"
    const val CAPABILITY_APPLICATION_LIST = "application_list"
    const val CAPABILITY_APPLICATION_DETAIL = "application_detail"
    const val CAPABILITY_APPLICATION_DIAGNOSTICS = "application_diagnostics"
    const val CONNECTION_SNAPSHOT_SCHEMA_VERSION = 1
    const val APPLICATION_QUERY_SCHEMA_VERSION = 1
    const val APPLICATION_PAGE_SCHEMA_VERSION = 1
    const val APPLICATION_SUMMARY_SCHEMA_VERSION = 1
    const val APPLICATION_STATS_SCHEMA_VERSION = 1
    const val APPLICATION_DETAIL_SCHEMA_VERSION = 1
    const val APPLICATION_DIAGNOSTICS_SCHEMA_VERSION = 1
    const val DEFAULT_MAX_PAGE_SIZE = 100
    const val DEFAULT_MAX_PAYLOAD_BYTES = 512 * 1024
    const val MAX_CAPABILITY_COUNT = 64
    const val MAX_CAPABILITY_LENGTH = 128
    const val MAX_RUNTIME_VERSION_NAME_LENGTH = 128
    const val MAX_COMPATIBILITY_REASON_LENGTH = 128
    const val MAX_WIRE_STRING_LENGTH = 4_096
    const val MAX_APPLICATION_QUERY_LENGTH = 512
    const val MAX_PACKAGE_NAME_LENGTH = 255
    const val MAX_PAGE_TOKEN_LENGTH = 1_024
    const val MAX_APPLICATION_PAGE_ITEM_COUNT = 1_000
    const val MAX_WIRE_FRAME_BYTES = DEFAULT_MAX_PAYLOAD_BYTES
    const val APPLICATION_FILTER_ALL = 0
    const val APPLICATION_FILTER_REGISTERED = 1
    const val APPLICATION_FILTER_NOT_REGISTERED = 2
    const val APPLICATION_FILTER_UNREGISTERED = 3
    const val MAX_NEGOTIATED_PAGE_SIZE = 1_000
    const val MAX_NEGOTIATED_PAYLOAD_BYTES = DEFAULT_MAX_PAYLOAD_BYTES

    const val CONNECTION_STATE_UNKNOWN = "Unknown"
    const val CONNECTION_STATE_IDLE = "Idle"
    const val CONNECTION_STATE_CONNECTING = "Connecting"
    const val CONNECTION_STATE_CONNECTED = "Connected"
    const val CONNECTION_STATE_DISCONNECTING = "Disconnecting"
    const val CONNECTION_STATE_DISCONNECTED = "Disconnected"

    val KNOWN_CAPABILITIES: Set<String> = setOf(
        CAPABILITY_CONNECTION_SNAPSHOT,
        CAPABILITY_APPLICATION_LIST,
        CAPABILITY_APPLICATION_DETAIL,
        CAPABILITY_APPLICATION_DIAGNOSTICS,
    )

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
        handshake.compatibilityReason?.let { reason ->
            reason.length > MAX_COMPATIBILITY_REASON_LENGTH ||
                reason.any { !it.isLetterOrDigit() && it != '_' && it != '-' && it != '.' }
        } == true -> "invalid_compatibility_reason"
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

    fun validateApplicationQuery(
        query: ManagerApplicationQueryDto,
        negotiatedMaxPageSize: Int,
    ): String? = when {
        query.schemaVersion < 1 -> "invalid_application_query_schema"
        negotiatedMaxPageSize !in 1..MAX_NEGOTIATED_PAGE_SIZE -> "invalid_negotiated_page_size"
        query.query.length > MAX_APPLICATION_QUERY_LENGTH -> "application_query_too_long"
        query.filterMode !in APPLICATION_FILTER_ALL..APPLICATION_FILTER_UNREGISTERED ->
            "invalid_application_filter_mode"
        query.pageSize !in 1..negotiatedMaxPageSize -> "invalid_application_page_size"
        query.pageToken?.length?.let { it > MAX_PAGE_TOKEN_LENGTH } == true -> "application_page_token_too_long"
        else -> null
    }

    fun validateApplicationPage(
        page: ManagerApplicationPageDto,
        negotiatedMaxPageSize: Int,
        negotiatedMaxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
    ): String? {
        if (page.schemaVersion < 1) return "invalid_application_page_schema"
        if (negotiatedMaxPageSize !in 1..MAX_NEGOTIATED_PAGE_SIZE) return "invalid_negotiated_page_size"
        if (negotiatedMaxPayloadBytes !in 1..MAX_NEGOTIATED_PAYLOAD_BYTES) {
            return "invalid_negotiated_payload_bytes"
        }
        if (page.items.size > negotiatedMaxPageSize) return "too_many_application_page_items"
        if (page.nextPageToken?.length?.let { it > MAX_PAGE_TOKEN_LENGTH } == true) {
            return "application_next_page_token_too_long"
        }
        page.items.forEach { summary ->
            validateApplicationSummary(summary)?.let { return it }
        }
        validateApplicationStats(page.stats)?.let { return it }
        if (estimateApplicationPageWireBytes(page) > negotiatedMaxPayloadBytes.toLong()) {
            return "application_page_payload_too_large"
        }
        return null
    }

    /** Conservative size estimate for the framed page payload, excluding Binder envelopes. */
    fun estimateApplicationPageWireBytes(page: ManagerApplicationPageDto): Long {
        val items = page.items.sumOf { summary ->
            val nullableIdBytes = if (summary.id == null) 0L else java.lang.Long.BYTES.toLong()
            APPLICATION_SUMMARY_FRAME_BYTES +
                nullableIdBytes +
                wireStringBytes(summary.packageName) +
                wireStringBytes(summary.appName) +
                wireStringBytes(summary.appNamePinYin)
        }
        val stats = APPLICATION_STATS_FRAME_BYTES
        return APPLICATION_PAGE_FIXED_BYTES + items + stats + wireStringBytes(page.nextPageToken)
    }

    fun validateApplicationSummary(summary: ManagerApplicationSummaryDto): String? =
        validateApplicationFields(
            schemaVersion = summary.schemaVersion,
            packageName = summary.packageName,
            appName = summary.appName,
            appNamePinYin = summary.appNamePinYin,
            schemaError = "invalid_application_summary_schema",
        )

    fun validateApplicationStats(stats: ManagerApplicationStatsDto): String? = when {
        stats.schemaVersion < 1 -> "invalid_application_stats_schema"
        stats.total < 0 || stats.usingMiPush < 0 || stats.notUsingMiPush < 0 ||
            stats.registered < 0 || stats.notRegistered < 0 -> "invalid_application_stats_count"
        stats.usingMiPush.toLong() + stats.notUsingMiPush.toLong() != stats.total.toLong() ->
            "inconsistent_application_stats_total"
        stats.registered.toLong() + stats.notRegistered.toLong() != stats.usingMiPush.toLong() ->
            "inconsistent_application_stats_registration"
        else -> null
    }

    fun validateApplicationDetail(detail: ManagerApplicationDetailDto): String? =
        validateApplicationFields(
            schemaVersion = detail.schemaVersion,
            packageName = detail.packageName,
            appName = detail.appName,
            appNamePinYin = detail.appNamePinYin,
            schemaError = "invalid_application_detail_schema",
        )

    fun validateApplicationDiagnostics(diagnostics: ManagerApplicationDiagnosticsDto): String? = when {
        diagnostics.schemaVersion < 1 -> "invalid_application_diagnostics_schema"
        diagnostics.regSecCount < 0 -> "invalid_application_diagnostics_reg_sec_count"
        diagnostics.inferenceReason.length > MAX_WIRE_STRING_LENGTH ->
            "application_diagnostics_inference_reason_too_long"
        else -> null
    }

    fun validateApplicationPackageName(packageName: String): String? = when {
        packageName.isBlank() || packageName.length > MAX_PACKAGE_NAME_LENGTH ->
            "invalid_application_package_name"
        packageName.any { !it.isLetterOrDigit() && it != '.' && it != '_' } ->
            "invalid_application_package_name"
        else -> null
    }

    fun validateApplicationDiagnosticsRequest(packageName: String, registeredType: Int): String? =
        validateApplicationPackageName(packageName) ?: when {
            registeredType !in 0..2 -> "invalid_application_registered_type"
            else -> null
        }

    private fun validateApplicationFields(
        schemaVersion: Int,
        packageName: String,
        appName: String,
        appNamePinYin: String,
        schemaError: String,
    ): String? = when {
        schemaVersion < 1 -> schemaError
        validateApplicationPackageName(packageName) != null -> "invalid_application_package_name"
        appName.length > MAX_WIRE_STRING_LENGTH -> "application_name_too_long"
        appNamePinYin.length > MAX_WIRE_STRING_LENGTH -> "application_name_pinyin_too_long"
        else -> null
    }

    private fun wireStringBytes(value: String?): Long {
        if (value == null) return Integer.BYTES.toLong()
        val charsWithTerminator = value.length.toLong() + 1L
        val bytes = Integer.BYTES.toLong() + charsWithTerminator * 2L
        return ((bytes + 3L) / 4L) * 4L
    }

    private const val APPLICATION_PAGE_FIXED_BYTES = 4L + 4L + 4L
    private const val APPLICATION_SUMMARY_FRAME_BYTES = 4L + 4L + 4L + 28L + 8L
    private const val APPLICATION_STATS_FRAME_BYTES = 4L + 6L * 4L

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
