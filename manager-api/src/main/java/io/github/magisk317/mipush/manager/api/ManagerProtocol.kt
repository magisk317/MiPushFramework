package io.github.magisk317.mipush.manager.api

object ManagerProtocol {
    const val MAJOR = 1
    const val MINOR = 7

    const val RUNTIME_PACKAGE = "com.xiaomi.xmsf"
    const val MANAGER_PACKAGE = "io.github.magisk317.mipush"
    const val SERVICE_ACTION = "io.github.magisk317.mipush.action.BIND_MANAGER_RUNTIME"
    const val SERVICE_PERMISSION = "com.xiaomi.xmsf.permission.BIND_MANAGER_RUNTIME"
    const val RUNTIME_SERVICE_CLASS = "io.github.magisk317.mipush.manager.runtime.ManagerRuntimeService"

    const val CAPABILITY_CONNECTION_SNAPSHOT = "connection_snapshot"
    const val CAPABILITY_APPLICATION_LIST = "application_list"
    const val CAPABILITY_APPLICATION_DETAIL = "application_detail"
    const val CAPABILITY_APPLICATION_DIAGNOSTICS = "application_diagnostics"
    const val CAPABILITY_EVENT_LIST = "event_list"
    const val CAPABILITY_NOTIFICATION_CHANNELS = "notification_channels"
    const val CAPABILITY_CONFIGURATION_CATALOG = "configuration_catalog"
    const val CAPABILITY_LOG_EXPORT = "log_export"
    const val CAPABILITY_RUNTIME_PREFERENCES = "runtime_preferences"
    const val CAPABILITY_MANAGER_MIGRATION_SNAPSHOT = "manager_migration_snapshot"
    const val CAPABILITY_CONFIGURATION_UPLOAD = "configuration_upload"
    const val CAPABILITY_WRITE_COMMANDS = "write_commands"
    const val CAPABILITY_RUNTIME_ENVIRONMENT = "runtime_environment"
    const val CONNECTION_SNAPSHOT_SCHEMA_VERSION = 1
    const val RUNTIME_ENVIRONMENT_SCHEMA_VERSION = 1
    const val APPLICATION_QUERY_SCHEMA_VERSION = 1
    const val APPLICATION_PAGE_SCHEMA_VERSION = 1
    const val APPLICATION_SUMMARY_SCHEMA_VERSION = 2
    const val APPLICATION_STATS_SCHEMA_VERSION = 1
    const val APPLICATION_DETAIL_SCHEMA_VERSION = 2
    const val APPLICATION_DIAGNOSTICS_SCHEMA_VERSION = 2
    const val EVENT_QUERY_SCHEMA_VERSION = 2
    const val EVENT_PAGE_SCHEMA_VERSION = 1
    const val EVENT_SUMMARY_SCHEMA_VERSION = 2
    const val NOTIFICATION_CHANNEL_QUERY_SCHEMA_VERSION = 1
    const val NOTIFICATION_CHANNEL_PAGE_SCHEMA_VERSION = 1
    const val NOTIFICATION_CHANNEL_SUMMARY_SCHEMA_VERSION = 1
    const val NOTIFICATION_CHANNEL_GROUP_SUMMARY_SCHEMA_VERSION = 1
    const val CONFIGURATION_CATALOG_SCHEMA_VERSION = 1
    const val CONFIGURATION_CATALOG_ENTRY_SCHEMA_VERSION = 1
    const val LOG_EXPORT_RESULT_SCHEMA_VERSION = 1
    const val RUNTIME_PREFERENCES_SCHEMA_VERSION = 1
    const val RUNTIME_PREFERENCE_ENTRY_SCHEMA_VERSION = 1
    const val MANAGER_MIGRATION_SNAPSHOT_SCHEMA_VERSION = 1
    const val CONFIGURATION_UPLOAD_REQUEST_SCHEMA_VERSION = 1
    const val CONFIGURATION_UPLOAD_RESULT_SCHEMA_VERSION = 1
    const val MAX_PREFERENCE_ENTRY_COUNT = 256
    const val MAX_PREFERENCE_KEY_LENGTH = 128
    const val MAX_PREFERENCE_VALUE_LENGTH = 4_096
    const val MAX_CONFIGURATION_UPLOAD_BYTES = 512 * 1024
    const val WRITE_REQUEST_SCHEMA_VERSION = 1
    const val WRITE_RESULT_SCHEMA_VERSION = 1
    const val MAX_WRITE_REQUEST_ID_LENGTH = 128
    const val MAX_WRITE_OPERATION_LENGTH = 64
    const val MAX_WRITE_ARGUMENT_LENGTH = 4_096
    const val WRITE_STATUS_SUCCESS = "success"
    const val WRITE_STATUS_FAILED = "failed"
    const val WRITE_STATUS_UNSUPPORTED = "unsupported"
    const val WRITE_STATUS_DUPLICATE = "duplicate"
    const val WRITE_DETAIL_MOCK_REPLAY_POSTED = "mock_replay_posted"
    const val WRITE_DETAIL_MOCK_REPLAY_DISPATCHED = "mock_replay_dispatched"
    const val WRITE_DETAIL_MOCK_REPLAY_FAILED = "mock_replay_failed"
    const val WRITE_DETAIL_MOCK_REPLAY_BLOCKED = "mock_replay_blocked_by_permission"
    const val WRITE_DETAIL_DUAL_APP_COMPLETED = "dual_app_completed"
    const val WRITE_DETAIL_DUAL_APP_ROOT_MISSING = "dual_app_root_missing"
    const val WRITE_DETAIL_DUAL_APP_PRIMARY_USER_REQUIRED = "dual_app_primary_user_required"
    const val WRITE_DETAIL_DUAL_APP_XSPACE_MISSING = "dual_app_xspace_user_not_found"
    const val WRITE_DETAIL_DUAL_APP_PARTIAL_FAILED = "dual_app_partial_failed"
    const val WRITE_DETAIL_DUAL_APP_INSTALLED = "dual_app_installed"
    const val WRITE_DETAIL_DUAL_APP_NOT_INSTALLED = "dual_app_not_installed"
    const val WRITE_OP_UPDATE_APPLICATION = "update_application"
    const val WRITE_OP_LAUNCH_TARGET_FORCE_REGISTER = "launch_target_force_register"
    const val WRITE_OP_DELETE_EVENT = "delete_event"
    const val WRITE_OP_RESTORE_EVENT = "restore_event"
    const val WRITE_OP_SET_XMPP_SERVER = "set_xmpp_server"
    const val WRITE_OP_CLEAR_HISTORY = "clear_history"
    const val WRITE_OP_SET_RUNTIME_LOG_RETENTION = "set_runtime_log_retention"
    const val WRITE_OP_APPLY_EVENT_RETENTION = "apply_event_retention"
    const val WRITE_OP_START_FOREGROUND = "start_foreground"
    const val WRITE_OP_XMPP_RECONNECT = "xmpp_reconnect"
    const val WRITE_OP_MOCK_MESSAGE = "mock_message"
    const val WRITE_OP_SET_DUAL_APP = "set_dual_app"
    const val WRITE_OP_QUERY_DUAL_APP = "query_dual_app"
    const val WRITE_OP_GRANT_SILENT_PERMISSIONS = "grant_silent_permissions"
    const val WRITE_OP_QUERY_USAGE_STATS = "query_usage_stats"
    const val WRITE_OP_QUERY_ROOT = "query_root"
    const val WRITE_OP_SYNC_LAUNCHER_ICON = "sync_launcher_icon"
    const val WRITE_OP_SET_RUNTIME_BOOLEAN = "set_runtime_boolean"
    const val WRITE_OP_SET_RUNTIME_INT = "set_runtime_int"
    const val WRITE_OP_SET_RUNTIME_STRING = "set_runtime_string"
    const val WRITE_OP_RESTART_RUNTIME = "restart_runtime"
    const val WRITE_OP_REBOOT_DEVICE = "reboot_device"
    const val WRITE_OP_RELAUNCH_MANAGER = "relaunch_manager"
    const val WRITE_OP_COUNT_EVENTS_BY_DAY = "count_events_by_day"
    const val WRITE_OP_CLEAR_LOG_FOLDERS = "clear_log_folders"
    const val WRITE_OP_DELETE_NOTIFICATION_CHANNEL = "delete_notification_channel"
    const val WRITE_OP_ZYGISK_IS_ENABLED = "zygisk_is_enabled"
    const val WRITE_OP_ZYGISK_GET_CONFIG = "zygisk_get_config"
    const val WRITE_OP_ZYGISK_SAVE_CONFIG = "zygisk_save_config"
    const val WRITE_OP_ZYGISK_FORCE_STOP = "zygisk_force_stop"
    const val WRITE_OP_ZYGISK_SCAN = "zygisk_scan"
    const val WRITE_OP_REPAIR_XSPACE = "repair_xspace"
    const val WRITE_OP_RESET_TOP_ACTIVITY_CACHE = "reset_top_activity_cache"
    const val WRITE_OP_GET_EVENT_CONTENT = "get_event_content"
    const val WRITE_OP_GET_EVENT_JSON = "get_event_json"
    const val WRITE_DETAIL_RELAUNCH_MANAGER_OK = "relaunch_manager_ok"
    const val WRITE_DETAIL_COUNT_EVENTS_BY_DAY_OK = "count_events_by_day_ok"
    const val WRITE_DETAIL_CLEAR_LOG_FOLDERS_OK = "clear_log_folders_ok"
    const val WRITE_DETAIL_CLEAR_LOG_FOLDERS_FAILED = "clear_log_folders_failed"
    const val WRITE_DETAIL_DELETE_NOTIFICATION_CHANNEL_OK = "delete_notification_channel_ok"
    const val WRITE_DETAIL_DELETE_NOTIFICATION_CHANNEL_FAILED = "delete_notification_channel_failed"
    const val WRITE_DETAIL_ZYGISK_OK = "zygisk_ok"
    const val WRITE_DETAIL_ZYGISK_ROOT_MISSING = "zygisk_root_missing"
    const val WRITE_DETAIL_ZYGISK_FAILED = "zygisk_failed"
    const val WRITE_DETAIL_REPAIR_XSPACE_OK = "repair_xspace_ok"
    const val WRITE_DETAIL_RESET_TOP_ACTIVITY_CACHE_OK = "reset_top_activity_cache_ok"
    const val WRITE_DETAIL_GET_EVENT_CONTENT_OK = "get_event_content_ok"
    const val WRITE_DETAIL_GET_EVENT_JSON_OK = "get_event_json_ok"
    const val WRITE_DETAIL_RELAUNCH_MANAGER_FAILED = "relaunch_manager_failed"
    const val WRITE_DETAIL_REBOOT_DEVICE_OK = "reboot_device_ok"
    const val WRITE_DETAIL_REBOOT_DEVICE_ROOT_MISSING = "reboot_device_root_missing"
    const val WRITE_DETAIL_SET_RUNTIME_BOOLEAN_OK = "set_runtime_boolean_ok"
    const val WRITE_DETAIL_SET_RUNTIME_INT_OK = "runtime_int_set"
    const val WRITE_DETAIL_SET_RUNTIME_INT_UNKNOWN_KEY = "runtime_int_unknown_key"
    const val WRITE_DETAIL_SET_RUNTIME_BOOLEAN_UNKNOWN_KEY = "set_runtime_boolean_unknown_key"
    const val WRITE_DETAIL_SET_RUNTIME_STRING_OK = "runtime_string_set"
    const val WRITE_DETAIL_SET_RUNTIME_STRING_UNKNOWN_KEY = "runtime_string_unknown_key"
    const val WRITE_DETAIL_RESTART_RUNTIME_OK = "restart_runtime_ok"
    const val WRITE_DETAIL_SYNC_LAUNCHER_ICON_OK = "sync_launcher_icon_ok"
    const val WRITE_DETAIL_SYNC_LAUNCHER_ICON_ROOT_MISSING = "sync_launcher_icon_root_missing"
    const val WRITE_DETAIL_SYNC_LAUNCHER_ICON_FAILED = "sync_launcher_icon_failed"
    const val WRITE_DETAIL_GRANT_SILENT_OK = "grant_silent_ok"
    const val WRITE_DETAIL_GRANT_SILENT_ROOT_MISSING = "grant_silent_root_missing"
    const val WRITE_DETAIL_GRANT_SILENT_FAILED = "grant_silent_failed"
    const val WRITE_DETAIL_ROOT_AVAILABLE = "root_available"
    const val WRITE_DETAIL_ROOT_MISSING = "root_missing"
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
    const val MAX_EVENT_PAGE_ITEM_COUNT = 1_000
    const val MAX_EVENT_PAYLOAD_BYTES = 256 * 1024
    const val MAX_EVENT_CONFIG_OPTION_COUNT = 64
    const val MAX_EVENT_CONFIG_OPTION_LENGTH = 128
    const val MAX_NOTIFICATION_CHANNEL_PAGE_ITEM_COUNT = 1_000
    const val MAX_NOTIFICATION_CHANNEL_GROUP_COUNT = 1_000
    const val MAX_CONFIGURATION_CATALOG_ITEM_COUNT = 2_000
    const val MAX_CONFIGURATION_PATH_LENGTH = 512
    const val MAX_CONFIGURATION_NAME_LENGTH = 255
    const val MAX_CONFIGURATION_SHA_LENGTH = 128
    const val MAX_LOG_EXPORT_DETAILS_LENGTH = 4_096
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
        CAPABILITY_EVENT_LIST,
        CAPABILITY_NOTIFICATION_CHANNELS,
        CAPABILITY_CONFIGURATION_CATALOG,
        CAPABILITY_LOG_EXPORT,
        CAPABILITY_RUNTIME_PREFERENCES,
        CAPABILITY_MANAGER_MIGRATION_SNAPSHOT,
        CAPABILITY_CONFIGURATION_UPLOAD,
        CAPABILITY_WRITE_COMMANDS,
        CAPABILITY_RUNTIME_ENVIRONMENT,
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

    fun validateRuntimeEnvironmentSnapshot(snapshot: ManagerRuntimeEnvironmentSnapshotDto): String? = when {
        snapshot.schemaVersion < 1 -> "invalid_runtime_environment_schema"
        snapshot.imei?.length?.let { it > MAX_WIRE_STRING_LENGTH } == true -> "imei_too_long"
        snapshot.macAddress?.length?.let { it > MAX_WIRE_STRING_LENGTH } == true -> "mac_address_too_long"
        snapshot.xmppServerHost.length > MAX_WIRE_STRING_LENGTH -> "xmpp_server_host_too_long"
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
        query.userId < 0 -> "invalid_application_user_id"
        query.pageToken?.length?.let { it > MAX_PAGE_TOKEN_LENGTH } == true -> "application_page_token_too_long"
        else -> null
    }

    fun validateApplicationPage(
        page: ManagerApplicationPageDto,
        negotiatedMaxPageSize: Int,
        negotiatedMaxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
    ): String? {
        if (page.schemaVersion < 1) return "invalid_application_page_schema"
        if (page.userId < 0) return "invalid_application_user_id"
        if (page.items.any { it.userId != page.userId }) return "invalid_application_user_id"
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
        if (summary.userId < 0) {
            "invalid_application_user_id"
        } else validateApplicationFields(
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
        if (detail.userId < 0) {
            "invalid_application_user_id"
        } else validateApplicationFields(
            schemaVersion = detail.schemaVersion,
            packageName = detail.packageName,
            appName = detail.appName,
            appNamePinYin = detail.appNamePinYin,
            schemaError = "invalid_application_detail_schema",
        )

    fun validateApplicationDiagnostics(diagnostics: ManagerApplicationDiagnosticsDto): String? = when {
        diagnostics.schemaVersion < 1 -> "invalid_application_diagnostics_schema"
        diagnostics.userId < 0 -> "invalid_application_user_id"
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


    fun validateEventQuery(
        query: ManagerEventQueryDto,
        negotiatedMaxPageSize: Int,
    ): String? = when {
        query.schemaVersion < 1 -> "invalid_event_query_schema"
        query.userId < 0 -> "invalid_event_user_id"
        query.pageSize !in 1..negotiatedMaxPageSize -> "invalid_event_page_size"
        query.query.length > MAX_APPLICATION_QUERY_LENGTH -> "event_query_too_long"
        query.packageName.isNotEmpty() && validateApplicationPackageName(query.packageName) != null ->
            "invalid_event_package_name"
        query.lastId != null && query.lastId < 0L -> "invalid_event_last_id"
        else -> null
    }

    fun validateEventSummary(summary: ManagerEventSummaryDto): String? = when {
        summary.schemaVersion < 1 -> "invalid_event_summary_schema"
        summary.userId < 0 -> "invalid_event_user_id"
        summary.id < 0L -> "invalid_event_id"
        validateApplicationPackageName(summary.packageName) != null && summary.packageName.isNotEmpty() ->
            "invalid_event_package_name"
        summary.title.length > MAX_WIRE_STRING_LENGTH -> "event_title_too_long"
        summary.content.length > MAX_WIRE_STRING_LENGTH -> "event_content_too_long"
        summary.channel.length > MAX_WIRE_STRING_LENGTH -> "event_channel_too_long"
        summary.appName?.length?.let { it > MAX_WIRE_STRING_LENGTH } == true -> "event_app_name_too_long"
        summary.info?.length?.let { it > MAX_WIRE_STRING_LENGTH } == true -> "event_info_too_long"
        summary.regSec?.length?.let { it > MAX_WIRE_STRING_LENGTH } == true -> "event_reg_sec_too_long"
        summary.configOptions.size > MAX_EVENT_CONFIG_OPTION_COUNT -> "too_many_event_config_options"
        summary.configOptions.any { it.length > MAX_EVENT_CONFIG_OPTION_LENGTH } ->
            "event_config_option_too_long"
        summary.payload != null && summary.payload.size > MAX_EVENT_PAYLOAD_BYTES ->
            "event_payload_too_large"
        else -> null
    }

    fun validateEventPage(
        page: ManagerEventPageDto,
        negotiatedMaxPageSize: Int,
        negotiatedMaxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
    ): String? {
        if (page.schemaVersion < 1) return "invalid_event_page_schema"
        if (negotiatedMaxPageSize !in 1..MAX_NEGOTIATED_PAGE_SIZE) return "invalid_negotiated_page_size"
        if (negotiatedMaxPayloadBytes !in 1..MAX_NEGOTIATED_PAYLOAD_BYTES) {
            return "invalid_negotiated_payload_bytes"
        }
        if (page.items.size > negotiatedMaxPageSize) return "too_many_event_page_items"
        page.items.forEach { summary ->
            validateEventSummary(summary)?.let { return it }
        }
        if (estimateEventPageWireBytes(page) > negotiatedMaxPayloadBytes.toLong()) {
            return "event_page_payload_too_large"
        }
        return null
    }

    fun estimateEventPageWireBytes(page: ManagerEventPageDto): Long {
        val items = page.items.fold(0L) { acc, summary ->
            acc + EVENT_SUMMARY_FRAME_BYTES +
                wireStringBytes(summary.packageName) +
                summary.configOptions.fold(0L) { optionAcc, option ->
                    optionAcc + wireStringBytes(option)
                } +
                wireStringBytes(summary.channel) +
                wireStringBytes(summary.title) +
                wireStringBytes(summary.content) +
                wireStringBytes(summary.appName) +
                wireStringBytes(summary.info) +
                (summary.payload?.size?.toLong() ?: 0L) +
                wireStringBytes(summary.regSec)
        }
        return EVENT_PAGE_FIXED_BYTES + items
    }

    fun validateNotificationChannelQuery(
        query: ManagerNotificationChannelQueryDto,
        negotiatedMaxPageSize: Int,
    ): String? = when {
        query.schemaVersion < 1 -> "invalid_notification_channel_query_schema"
        validateApplicationPackageName(query.packageName) != null -> "invalid_notification_channel_package_name"
        query.pageSize !in 1..negotiatedMaxPageSize -> "invalid_notification_channel_page_size"
        query.userId < 0 -> "invalid_notification_channel_user_id"
        query.pageToken?.length?.let { it > MAX_PAGE_TOKEN_LENGTH } == true ->
            "notification_channel_page_token_too_long"
        else -> null
    }

    fun validateNotificationChannelSummary(summary: ManagerNotificationChannelSummaryDto): String? = when {
        summary.schemaVersion < 1 -> "invalid_notification_channel_summary_schema"
        summary.id.isBlank() || summary.id.length > MAX_WIRE_STRING_LENGTH ->
            "invalid_notification_channel_id"
        summary.name.length > MAX_WIRE_STRING_LENGTH -> "notification_channel_name_too_long"
        summary.groupId?.length?.let { it > MAX_WIRE_STRING_LENGTH } == true ->
            "notification_channel_group_id_too_long"
        summary.description?.length?.let { it > MAX_WIRE_STRING_LENGTH } == true ->
            "notification_channel_description_too_long"
        else -> null
    }

    fun validateNotificationChannelGroupSummary(group: ManagerNotificationChannelGroupSummaryDto): String? =
        when {
            group.schemaVersion < 1 -> "invalid_notification_channel_group_schema"
            group.id.isBlank() || group.id.length > MAX_WIRE_STRING_LENGTH ->
                "invalid_notification_channel_group_id"
            group.name.length > MAX_WIRE_STRING_LENGTH -> "notification_channel_group_name_too_long"
            else -> null
        }

    fun validateNotificationChannelPage(
        page: ManagerNotificationChannelPageDto,
        negotiatedMaxPageSize: Int,
        negotiatedMaxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
    ): String? {
        if (page.schemaVersion < 1) return "invalid_notification_channel_page_schema"
        if (page.userId < 0) return "invalid_notification_channel_user_id"
        if (negotiatedMaxPageSize !in 1..MAX_NEGOTIATED_PAGE_SIZE) return "invalid_negotiated_page_size"
        if (page.items.size > negotiatedMaxPageSize) return "too_many_notification_channel_page_items"
        if (page.groups.size > MAX_NOTIFICATION_CHANNEL_GROUP_COUNT) {
            return "too_many_notification_channel_groups"
        }
        if (page.nextPageToken?.length?.let { it > MAX_PAGE_TOKEN_LENGTH } == true) {
            return "notification_channel_next_page_token_too_long"
        }
        page.items.forEach { validateNotificationChannelSummary(it)?.let { reason -> return reason } }
        page.groups.forEach { validateNotificationChannelGroupSummary(it)?.let { reason -> return reason } }
        if (estimateNotificationChannelPageWireBytes(page) > negotiatedMaxPayloadBytes.toLong()) {
            return "notification_channel_page_payload_too_large"
        }
        return null
    }

    fun estimateNotificationChannelPageWireBytes(page: ManagerNotificationChannelPageDto): Long {
        val items = page.items.fold(0L) { acc, summary ->
            acc + NOTIFICATION_CHANNEL_SUMMARY_FRAME_BYTES +
                wireStringBytes(summary.id) +
                wireStringBytes(summary.name) +
                wireStringBytes(summary.groupId) +
                wireStringBytes(summary.description)
        }
        val groups = page.groups.fold(0L) { acc, group ->
            acc + NOTIFICATION_CHANNEL_GROUP_FRAME_BYTES +
                wireStringBytes(group.id) +
                wireStringBytes(group.name)
        }
        return NOTIFICATION_CHANNEL_PAGE_FIXED_BYTES + items + groups + wireStringBytes(page.nextPageToken)
    }

    fun validateConfigurationCatalog(catalog: ManagerConfigurationCatalogDto): String? {
        if (catalog.schemaVersion < 1) return "invalid_configuration_catalog_schema"
        if (catalog.sourceRepo.length > MAX_WIRE_STRING_LENGTH) return "configuration_source_repo_too_long"
        if (catalog.branch.length > MAX_WIRE_STRING_LENGTH) return "configuration_branch_too_long"
        if (catalog.generatedAt.length > MAX_WIRE_STRING_LENGTH) return "configuration_generated_at_too_long"
        if (catalog.files.size > MAX_CONFIGURATION_CATALOG_ITEM_COUNT) {
            return "too_many_configuration_catalog_items"
        }
        catalog.files.forEach { entry ->
            validateConfigurationCatalogEntry(entry)?.let { return it }
        }
        return null
    }

    fun validateConfigurationCatalogEntry(entry: ManagerConfigurationCatalogEntryDto): String? = when {
        entry.schemaVersion < 1 -> "invalid_configuration_catalog_entry_schema"
        entry.path.isBlank() || entry.path.length > MAX_CONFIGURATION_PATH_LENGTH ->
            "invalid_configuration_path"
        entry.name.length > MAX_CONFIGURATION_NAME_LENGTH -> "configuration_name_too_long"
        entry.sha.length > MAX_CONFIGURATION_SHA_LENGTH -> "configuration_sha_too_long"
        entry.size < 0 -> "invalid_configuration_size"
        entry.updatedAt.length > MAX_WIRE_STRING_LENGTH -> "configuration_updated_at_too_long"
        else -> null
    }

    fun validateLogExportResult(result: ManagerLogExportResultDto): String? = when {
        result.schemaVersion < 1 -> "invalid_log_export_result_schema"
        result.details.length > MAX_LOG_EXPORT_DETAILS_LENGTH -> "log_export_details_too_long"
        result.success && result.parcelFileDescriptor == null -> "log_export_missing_descriptor"
        else -> null
    }


    fun validateRuntimePreferences(snapshot: ManagerRuntimePreferencesDto): String? {
        if (snapshot.schemaVersion < 1) return "invalid_runtime_preferences_schema"
        if (snapshot.entries.size > MAX_PREFERENCE_ENTRY_COUNT) return "too_many_runtime_preferences"
        snapshot.entries.forEach { entry ->
            validatePreferenceEntry(entry, requireRuntimeOwner = true)?.let { return it }
        }
        return null
    }

    fun validateManagerMigrationSnapshot(snapshot: ManagerMigrationSnapshotDto): String? {
        if (snapshot.schemaVersion < 1) return "invalid_manager_migration_snapshot_schema"
        if (snapshot.entries.size > MAX_PREFERENCE_ENTRY_COUNT) return "too_many_migration_preferences"
        snapshot.entries.forEach { entry ->
            validatePreferenceEntry(entry, requireRuntimeOwner = false)?.let { return it }
        }
        return null
    }

    fun validateConfigurationUploadRequest(request: ManagerConfigurationUploadRequestDto): String? = when {
        request.schemaVersion < 1 -> "invalid_configuration_upload_request_schema"
        request.path.isBlank() || request.path.length > MAX_CONFIGURATION_PATH_LENGTH ->
            "invalid_configuration_upload_path"
        request.path.contains("..") || request.path.startsWith("/") ->
            "invalid_configuration_upload_path"
        request.contentLength !in 0..MAX_CONFIGURATION_UPLOAD_BYTES ->
            "invalid_configuration_upload_size"
        request.parcelFileDescriptor == null -> "configuration_upload_missing_descriptor"
        else -> null
    }

    fun validateConfigurationUploadResult(result: ManagerConfigurationUploadResultDto): String? = when {
        result.schemaVersion < 1 -> "invalid_configuration_upload_result_schema"
        result.details.length > MAX_LOG_EXPORT_DETAILS_LENGTH -> "configuration_upload_details_too_long"
        else -> null
    }

    private fun validatePreferenceEntry(
        entry: ManagerPreferenceEntryDto,
        requireRuntimeOwner: Boolean,
    ): String? = when {
        entry.schemaVersion < 1 -> "invalid_preference_entry_schema"
        entry.key.isBlank() || entry.key.length > MAX_PREFERENCE_KEY_LENGTH -> "invalid_preference_key"
        entry.value.length > MAX_PREFERENCE_VALUE_LENGTH -> "preference_value_too_long"
        entry.type !in setOf("string", "boolean", "int", "long", "float") -> "invalid_preference_type"
        requireRuntimeOwner && entry.owner != "runtime" -> "preference_not_runtime_owned"
        !requireRuntimeOwner && entry.owner != "manager" -> "preference_not_manager_owned"
        else -> null
    }


    fun validateWriteRequest(request: ManagerWriteRequestDto): String? = when {
        request.schemaVersion < 1 -> "invalid_write_request_schema"
        request.requestId.isBlank() || request.requestId.length > MAX_WRITE_REQUEST_ID_LENGTH ->
            "invalid_write_request_id"
        request.operation.isBlank() || request.operation.length > MAX_WRITE_OPERATION_LENGTH ->
            "invalid_write_operation"
        request.argument.length > MAX_WRITE_ARGUMENT_LENGTH -> "write_argument_too_long"
        request.packageName.isNotEmpty() && validateApplicationPackageName(request.packageName) != null ->
            "invalid_write_package_name"
        request.operation in WRITE_OPERATIONS_REQUIRING_PACKAGE &&
            validateApplicationPackageName(request.packageName) != null ->
            "write_package_name_required"
        request.operation in WRITE_OPERATIONS_REQUIRING_EVENT_ID &&
            (request.eventId == null || request.eventId <= 0L) ->
            "invalid_write_event_id"
        request.operation in WRITE_OPERATIONS_REQUIRING_USER && request.userId < 0 ->
            "invalid_write_user_id"
        request.eventId != null && request.eventId < 0L -> "invalid_write_event_id"
        else -> null
    }

    private val WRITE_OPERATIONS_REQUIRING_PACKAGE = setOf(
        WRITE_OP_UPDATE_APPLICATION,
        WRITE_OP_LAUNCH_TARGET_FORCE_REGISTER,
        WRITE_OP_DELETE_EVENT,
        WRITE_OP_RESTORE_EVENT,
        WRITE_OP_MOCK_MESSAGE,
        WRITE_OP_QUERY_USAGE_STATS,
        WRITE_OP_DELETE_NOTIFICATION_CHANNEL,
        WRITE_OP_ZYGISK_FORCE_STOP,
        WRITE_OP_GET_EVENT_CONTENT,
        WRITE_OP_GET_EVENT_JSON,
    )

    private val WRITE_OPERATIONS_REQUIRING_EVENT_ID = setOf(
        WRITE_OP_DELETE_EVENT,
        WRITE_OP_RESTORE_EVENT,
        WRITE_OP_MOCK_MESSAGE,
        WRITE_OP_GET_EVENT_CONTENT,
        WRITE_OP_GET_EVENT_JSON,
    )

    private val WRITE_OPERATIONS_REQUIRING_USER = setOf(
        WRITE_OP_DELETE_EVENT,
        WRITE_OP_RESTORE_EVENT,
        WRITE_OP_MOCK_MESSAGE,
        WRITE_OP_GET_EVENT_CONTENT,
        WRITE_OP_GET_EVENT_JSON,
    )

    fun validateWriteResult(result: ManagerWriteResultDto): String? = when {
        result.schemaVersion < 1 -> "invalid_write_result_schema"
        result.requestId.isBlank() || result.requestId.length > MAX_WRITE_REQUEST_ID_LENGTH ->
            "invalid_write_result_request_id"
        result.status !in setOf(
            WRITE_STATUS_SUCCESS,
            WRITE_STATUS_FAILED,
            WRITE_STATUS_UNSUPPORTED,
            WRITE_STATUS_DUPLICATE,
        ) -> "invalid_write_status"
        result.details.length > MAX_LOG_EXPORT_DETAILS_LENGTH -> "write_details_too_long"
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

    private const val APPLICATION_PAGE_FIXED_BYTES = 4L + 4L + 4L + 4L
    private const val APPLICATION_SUMMARY_FRAME_BYTES = 4L + 4L + 4L + 28L + 8L
    private const val APPLICATION_STATS_FRAME_BYTES = 4L + 6L * 4L
    private const val EVENT_PAGE_FIXED_BYTES = 4L + 4L
    private const val EVENT_SUMMARY_FRAME_BYTES = 4L + 8L + 8L + 8L + 8L + 8L
    private const val NOTIFICATION_CHANNEL_PAGE_FIXED_BYTES = 4L + 4L + 4L + 4L + 4L
    private const val NOTIFICATION_CHANNEL_SUMMARY_FRAME_BYTES = 4L + 4L + 12L
    private const val NOTIFICATION_CHANNEL_GROUP_FRAME_BYTES = 4L + 4L

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
