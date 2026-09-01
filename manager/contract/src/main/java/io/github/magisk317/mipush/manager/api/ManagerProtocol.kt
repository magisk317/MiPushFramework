package io.github.magisk317.mipush.manager.api

import io.github.magisk317.mipush.manager.ApplicationDiagnosticsValidationInput
import io.github.magisk317.mipush.manager.ApplicationDetailValidationInput
import io.github.magisk317.mipush.manager.ApplicationQueryValidationInput
import io.github.magisk317.mipush.manager.ApplicationSummaryValidationInput
import io.github.magisk317.mipush.manager.ApplicationStatsValidationInput
import io.github.magisk317.mipush.manager.ConnectionSnapshotValidationInput
import io.github.magisk317.mipush.manager.ConfigurationCatalogEntryValidationInput
import io.github.magisk317.mipush.manager.ConfigurationCatalogValidationInput
import io.github.magisk317.mipush.manager.EventPageValidationInput
import io.github.magisk317.mipush.manager.EventQueryValidationInput
import io.github.magisk317.mipush.manager.EventSummaryValidationInput
import io.github.magisk317.mipush.manager.ManagerContractValidationCore
import io.github.magisk317.mipush.manager.ManagerContractLimits
import io.github.magisk317.mipush.manager.NotificationChannelGroupValidationInput
import io.github.magisk317.mipush.manager.NotificationChannelPageValidationInput
import io.github.magisk317.mipush.manager.NotificationChannelQueryValidationInput
import io.github.magisk317.mipush.manager.NotificationChannelSummaryValidationInput
import io.github.magisk317.mipush.manager.RuntimeEnvironmentValidationInput

object ManagerProtocol {
    const val MAJOR = 1
    const val MINOR = 7

    const val RUNTIME_PACKAGE = "com.xiaomi.xmsf"
    const val MANAGER_PACKAGE = "io.github.magisk317.mipush"
    /** Values carried by [ManagerWriteRequestDto.intArgument] for silent grants. */
    const val GRANT_USER_PRIMARY = 0
    const val GRANT_USER_XSPACE = 999
    const val GRANT_USER_AUTO = -1
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
    const val MAX_PREFERENCE_ENTRY_COUNT = ManagerContractLimits.MAX_PREFERENCE_ENTRY_COUNT
    const val MAX_PREFERENCE_KEY_LENGTH = ManagerContractLimits.MAX_PREFERENCE_KEY_LENGTH
    const val MAX_PREFERENCE_VALUE_LENGTH = ManagerContractLimits.MAX_PREFERENCE_VALUE_LENGTH
    const val MAX_CONFIGURATION_UPLOAD_BYTES = ManagerContractLimits.MAX_CONFIGURATION_UPLOAD_BYTES
    const val WRITE_REQUEST_SCHEMA_VERSION = 1
    const val WRITE_RESULT_SCHEMA_VERSION = 1
    const val MAX_WRITE_REQUEST_ID_LENGTH = ManagerContractLimits.MAX_WRITE_REQUEST_ID_LENGTH
    const val MAX_WRITE_OPERATION_LENGTH = ManagerContractLimits.MAX_WRITE_OPERATION_LENGTH
    const val MAX_WRITE_ARGUMENT_LENGTH = ManagerContractLimits.MAX_WRITE_ARGUMENT_LENGTH
    const val WRITE_STATUS_SUCCESS = "success"
    const val WRITE_STATUS_FAILED = "failed"
    const val WRITE_STATUS_UNSUPPORTED = "unsupported"
    const val WRITE_STATUS_DUPLICATE = "duplicate"
    const val WRITE_DETAIL_MOCK_REPLAY_POSTED = "mock_replay_posted"
    const val WRITE_DETAIL_MOCK_REPLAY_DISPATCHED = "mock_replay_dispatched"
    const val WRITE_DETAIL_MOCK_REPLAY_FAILED = "mock_replay_failed"
    const val WRITE_DETAIL_MOCK_REPLAY_FAILED_CHANNEL_DISABLED = "mock_replay_failed_channel_disabled"
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
    const val WRITE_OP_SET_RUNTIME_BOOLEAN = "set_runtime_boolean"
    const val WRITE_OP_SET_RUNTIME_INT = "set_runtime_int"
    const val WRITE_OP_RESTART_RUNTIME = "restart_runtime"
    const val WRITE_OP_REBOOT_DEVICE = "reboot_device"
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
    const val WRITE_DETAIL_REBOOT_DEVICE_OK = "reboot_device_ok"
    const val WRITE_DETAIL_REBOOT_DEVICE_ROOT_MISSING = "reboot_device_root_missing"
    const val WRITE_DETAIL_SET_RUNTIME_BOOLEAN_OK = "set_runtime_boolean_ok"
    const val WRITE_DETAIL_SET_RUNTIME_INT_OK = "runtime_int_set"
    const val WRITE_DETAIL_SET_RUNTIME_INT_UNKNOWN_KEY = "runtime_int_unknown_key"
    const val WRITE_DETAIL_SET_RUNTIME_BOOLEAN_UNKNOWN_KEY = "set_runtime_boolean_unknown_key"
    const val WRITE_DETAIL_RESTART_RUNTIME_OK = "restart_runtime_ok"
    const val WRITE_DETAIL_GRANT_SILENT_OK = "grant_silent_ok"
    const val WRITE_DETAIL_GRANT_SILENT_ROOT_MISSING = "grant_silent_root_missing"
    const val WRITE_DETAIL_GRANT_SILENT_FAILED = "grant_silent_failed"
    const val WRITE_DETAIL_ROOT_AVAILABLE = "root_available"
    const val WRITE_DETAIL_ROOT_MISSING = "root_missing"
    const val DEFAULT_MAX_PAGE_SIZE = ManagerContractLimits.DEFAULT_MAX_PAGE_SIZE
    const val DEFAULT_MAX_PAYLOAD_BYTES = ManagerContractLimits.DEFAULT_MAX_PAYLOAD_BYTES
    const val MAX_CAPABILITY_COUNT = ManagerContractLimits.MAX_CAPABILITY_COUNT
    const val MAX_CAPABILITY_LENGTH = ManagerContractLimits.MAX_CAPABILITY_LENGTH
    const val MAX_RUNTIME_VERSION_NAME_LENGTH = ManagerContractLimits.MAX_RUNTIME_VERSION_NAME_LENGTH
    const val MAX_COMPATIBILITY_REASON_LENGTH = ManagerContractLimits.MAX_COMPATIBILITY_REASON_LENGTH
    const val MAX_WIRE_STRING_LENGTH = ManagerContractLimits.MAX_WIRE_STRING_LENGTH
    const val MAX_APPLICATION_QUERY_LENGTH = ManagerContractLimits.MAX_APPLICATION_QUERY_LENGTH
    const val MAX_PACKAGE_NAME_LENGTH = ManagerContractLimits.MAX_PACKAGE_NAME_LENGTH
    const val MAX_PAGE_TOKEN_LENGTH = ManagerContractLimits.MAX_PAGE_TOKEN_LENGTH
    const val MAX_APPLICATION_PAGE_ITEM_COUNT = ManagerContractLimits.MAX_APPLICATION_PAGE_ITEM_COUNT
    const val MAX_EVENT_PAGE_ITEM_COUNT = ManagerContractLimits.MAX_EVENT_PAGE_ITEM_COUNT
    const val MAX_EVENT_PAYLOAD_BYTES = ManagerContractLimits.MAX_EVENT_PAYLOAD_BYTES
    const val MAX_EVENT_CONFIG_OPTION_COUNT = ManagerContractLimits.MAX_EVENT_CONFIG_OPTION_COUNT
    const val MAX_EVENT_CONFIG_OPTION_LENGTH = ManagerContractLimits.MAX_EVENT_CONFIG_OPTION_LENGTH
    const val MAX_NOTIFICATION_CHANNEL_PAGE_ITEM_COUNT = ManagerContractLimits.MAX_NOTIFICATION_CHANNEL_PAGE_ITEM_COUNT
    const val MAX_NOTIFICATION_CHANNEL_GROUP_COUNT = ManagerContractLimits.MAX_NOTIFICATION_CHANNEL_GROUP_COUNT
    const val MAX_CONFIGURATION_CATALOG_ITEM_COUNT = ManagerContractLimits.MAX_CONFIGURATION_CATALOG_ITEM_COUNT
    const val MAX_CONFIGURATION_PATH_LENGTH = ManagerContractLimits.MAX_CONFIGURATION_PATH_LENGTH
    const val MAX_CONFIGURATION_NAME_LENGTH = ManagerContractLimits.MAX_CONFIGURATION_NAME_LENGTH
    const val MAX_CONFIGURATION_SHA_LENGTH = ManagerContractLimits.MAX_CONFIGURATION_SHA_LENGTH
    const val MAX_LOG_EXPORT_DETAILS_LENGTH = ManagerContractLimits.MAX_LOG_EXPORT_DETAILS_LENGTH
    const val MAX_WIRE_FRAME_BYTES = ManagerContractLimits.MAX_WIRE_FRAME_BYTES
    const val APPLICATION_FILTER_ALL = 0
    const val APPLICATION_FILTER_REGISTERED = 1
    const val APPLICATION_FILTER_NOT_REGISTERED = 2
    const val APPLICATION_FILTER_UNREGISTERED = 3
    const val MAX_NEGOTIATED_PAGE_SIZE = ManagerContractLimits.MAX_NEGOTIATED_PAGE_SIZE
    const val MAX_NEGOTIATED_PAYLOAD_BYTES = ManagerContractLimits.MAX_NEGOTIATED_PAYLOAD_BYTES

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
        val result = ManagerProtocolNegotiationSupport.evaluateCompatibility(
            clientMajor,
            clientMinor,
            runtimeMajor,
            runtimeMinor,
        )
        return Compatibility(
            status = when (result.status) {
                NegotiationStatus.COMPATIBLE -> CompatibilityStatus.COMPATIBLE
                NegotiationStatus.INVALID_VERSION -> CompatibilityStatus.INVALID_VERSION
                NegotiationStatus.MAJOR_MISMATCH -> CompatibilityStatus.MAJOR_MISMATCH
            },
            negotiatedMinor = result.negotiatedMinor,
        )
    }

    fun recognizedCapabilities(runtimeCapabilities: Iterable<String>): Set<String> =
        ManagerProtocolNegotiationSupport.recognizedCapabilities(runtimeCapabilities, KNOWN_CAPABILITIES)

    fun validateHandshake(handshake: ManagerHandshake): String? =
        ManagerProtocolNegotiationSupport.validateHandshake(
            input = HandshakeValidationInput(
                protocolMajor = handshake.protocolMajor,
                protocolMinor = handshake.protocolMinor,
                runtimeVersionName = handshake.runtimeVersionName,
                runtimeVersionCode = handshake.runtimeVersionCode,
                supportedCapabilities = handshake.supportedCapabilities,
                maxPageSize = handshake.maxPageSize,
                maxPayloadBytes = handshake.maxPayloadBytes,
                compatibilityReason = handshake.compatibilityReason,
            ),
            limits = HandshakeValidationLimits(
                maxRuntimeVersionNameLength = MAX_RUNTIME_VERSION_NAME_LENGTH,
                maxCompatibilityReasonLength = MAX_COMPATIBILITY_REASON_LENGTH,
                maxCapabilityCount = MAX_CAPABILITY_COUNT,
                maxCapabilityLength = MAX_CAPABILITY_LENGTH,
                maxNegotiatedPageSize = MAX_NEGOTIATED_PAGE_SIZE,
                maxNegotiatedPayloadBytes = MAX_NEGOTIATED_PAYLOAD_BYTES,
            ),
        )

    fun validateConnectionSnapshot(snapshot: ManagerConnectionSnapshotDto): String? =
        ManagerContractValidationCore.validateConnectionSnapshot(
            ConnectionSnapshotValidationInput(
                schemaVersion = snapshot.schemaVersion,
                connectionState = snapshot.connectionState,
                serverHost = snapshot.serverHost,
                serverIp = snapshot.serverIp,
            ),
        )

    fun validateRuntimeEnvironmentSnapshot(snapshot: ManagerRuntimeEnvironmentSnapshotDto): String? =
        ManagerContractValidationCore.validateRuntimeEnvironment(
            RuntimeEnvironmentValidationInput(
                schemaVersion = snapshot.schemaVersion,
                imei = snapshot.imei,
                macAddress = snapshot.macAddress,
                xmppServerHost = snapshot.xmppServerHost,
            ),
        )

    fun validateApplicationQuery(query: ManagerApplicationQueryDto, negotiatedMaxPageSize: Int): String? =
        ManagerApplicationContractValidation.validateQuery(query, negotiatedMaxPageSize)

    fun validateApplicationPage(page: ManagerApplicationPageDto, negotiatedMaxPageSize: Int, negotiatedMaxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES): String? =
        ManagerApplicationContractValidation.validatePage(page, negotiatedMaxPageSize, negotiatedMaxPayloadBytes)

    fun estimateApplicationPageWireBytes(page: ManagerApplicationPageDto): Long =
        ManagerApplicationContractValidation.estimatePageWireBytes(page)

    fun validateApplicationSummary(summary: ManagerApplicationSummaryDto): String? =
        ManagerApplicationContractValidation.validateSummary(summary)

    fun validateApplicationStats(stats: ManagerApplicationStatsDto): String? =
        ManagerApplicationContractValidation.validateStats(stats)

    fun validateApplicationDetail(detail: ManagerApplicationDetailDto): String? =
        ManagerApplicationContractValidation.validateDetail(detail)

    fun validateApplicationDiagnostics(diagnostics: ManagerApplicationDiagnosticsDto): String? =
        ManagerApplicationContractValidation.validateDiagnostics(diagnostics)

    fun validateApplicationPackageName(packageName: String): String? =
        ManagerApplicationContractValidation.validatePackageName(packageName)

    fun validateApplicationDiagnosticsRequest(packageName: String, registeredType: Int): String? =
        ManagerApplicationContractValidation.validateDiagnosticsRequest(packageName, registeredType)


    fun validateEventQuery(
        query: ManagerEventQueryDto,
        negotiatedMaxPageSize: Int,
    ): String? = ManagerContractValidationCore.validateEventQuery(
        EventQueryValidationInput(
            schemaVersion = query.schemaVersion,
            lastId = query.lastId,
            pageSize = query.pageSize,
            packageName = query.packageName,
            query = query.query,
            userId = query.userId,
            negotiatedMaxPageSize = negotiatedMaxPageSize,
        ),
    )

    fun validateEventSummary(summary: ManagerEventSummaryDto): String? =
        ManagerContractValidationCore.validateEventSummary(
            EventSummaryValidationInput(
                schemaVersion = summary.schemaVersion,
                id = summary.id,
                packageName = summary.packageName,
                configOptions = summary.configOptions,
                channel = summary.channel,
                title = summary.title,
                content = summary.content,
                appName = summary.appName,
                info = summary.info,
                payloadBytes = summary.payload?.size ?: 0,
                regSec = summary.regSec,
                userId = summary.userId,
            ),
        )

    fun validateEventPage(
        page: ManagerEventPageDto,
        negotiatedMaxPageSize: Int,
        negotiatedMaxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
    ): String? = ManagerContractValidationCore.validateEventPage(
        EventPageValidationInput(
            schemaVersion = page.schemaVersion,
            summaries = page.items.map { summary ->
                EventSummaryValidationInput(
                    schemaVersion = summary.schemaVersion,
                    id = summary.id,
                    packageName = summary.packageName,
                    configOptions = summary.configOptions,
                    channel = summary.channel,
                    title = summary.title,
                    content = summary.content,
                    appName = summary.appName,
                    info = summary.info,
                    payloadBytes = summary.payload?.size ?: 0,
                    regSec = summary.regSec,
                    userId = summary.userId,
                )
            },
            negotiatedMaxPageSize = negotiatedMaxPageSize,
            negotiatedMaxPayloadBytes = negotiatedMaxPayloadBytes,
            wireSize = page.items.map { summary ->
                EventSummaryWireSizeInput(
                    packageName = summary.packageName,
                    configOptions = summary.configOptions,
                    channel = summary.channel,
                    title = summary.title,
                    content = summary.content,
                    appName = summary.appName,
                    info = summary.info,
                    payloadBytes = summary.payload?.size ?: 0,
                    regSec = summary.regSec,
                )
            },
        ),
    )

    fun estimateEventPageWireBytes(page: ManagerEventPageDto): Long =
        ManagerWireSize.estimateEventPage(
            page.items.map { summary ->
                EventSummaryWireSizeInput(
                    packageName = summary.packageName,
                    configOptions = summary.configOptions,
                    channel = summary.channel,
                    title = summary.title,
                    content = summary.content,
                    appName = summary.appName,
                    info = summary.info,
                    payloadBytes = summary.payload?.size ?: 0,
                    regSec = summary.regSec,
                )
            },
        )

    fun validateNotificationChannelQuery(
        query: ManagerNotificationChannelQueryDto,
        negotiatedMaxPageSize: Int,
    ): String? = ManagerContractValidationCore.validateNotificationChannelQuery(
        NotificationChannelQueryValidationInput(
            schemaVersion = query.schemaVersion,
            packageName = query.packageName,
            pageSize = query.pageSize,
            pageToken = query.pageToken,
            userId = query.userId,
            negotiatedMaxPageSize = negotiatedMaxPageSize,
        ),
    )

    fun validateNotificationChannelSummary(summary: ManagerNotificationChannelSummaryDto): String? =
        ManagerContractValidationCore.validateNotificationChannelSummary(
            NotificationChannelSummaryValidationInput(
                schemaVersion = summary.schemaVersion,
                id = summary.id,
                name = summary.name,
                groupId = summary.groupId,
                description = summary.description,
            ),
        )

    fun validateNotificationChannelGroupSummary(group: ManagerNotificationChannelGroupSummaryDto): String? =
        ManagerContractValidationCore.validateNotificationChannelGroup(
            NotificationChannelGroupValidationInput(
                schemaVersion = group.schemaVersion,
                id = group.id,
                name = group.name,
            ),
        )

    fun validateNotificationChannelPage(
        page: ManagerNotificationChannelPageDto,
        negotiatedMaxPageSize: Int,
        negotiatedMaxPayloadBytes: Int = DEFAULT_MAX_PAYLOAD_BYTES,
    ): String? = ManagerContractValidationCore.validateNotificationChannelPage(
        NotificationChannelPageValidationInput(
            schemaVersion = page.schemaVersion,
            userId = page.userId,
            items = page.items.map { summary ->
                NotificationChannelSummaryValidationInput(
                    schemaVersion = summary.schemaVersion,
                    id = summary.id,
                    name = summary.name,
                    groupId = summary.groupId,
                    description = summary.description,
                )
            },
            groups = page.groups.map { group ->
                NotificationChannelGroupValidationInput(
                    schemaVersion = group.schemaVersion,
                    id = group.id,
                    name = group.name,
                )
            },
            nextPageToken = page.nextPageToken,
            negotiatedMaxPageSize = negotiatedMaxPageSize,
            negotiatedMaxPayloadBytes = negotiatedMaxPayloadBytes,
            wireSize = NotificationChannelPageWireSizeInput(
                items = page.items.map { summary ->
                    NotificationChannelSummaryWireSizeInput(
                        id = summary.id,
                        name = summary.name,
                        groupId = summary.groupId,
                        description = summary.description,
                    )
                },
                groups = page.groups.map { group ->
                    NotificationChannelGroupWireSizeInput(
                        id = group.id,
                        name = group.name,
                    )
                },
                nextPageToken = page.nextPageToken,
            ),
        ),
    )

    fun estimateNotificationChannelPageWireBytes(page: ManagerNotificationChannelPageDto): Long =
        ManagerWireSize.estimateNotificationChannelPage(
            NotificationChannelPageWireSizeInput(
                items = page.items.map { summary ->
                    NotificationChannelSummaryWireSizeInput(
                        id = summary.id,
                        name = summary.name,
                        groupId = summary.groupId,
                        description = summary.description,
                    )
                },
                groups = page.groups.map { group ->
                    NotificationChannelGroupWireSizeInput(
                        id = group.id,
                        name = group.name,
                    )
                },
                nextPageToken = page.nextPageToken,
            ),
        )

    fun validateConfigurationCatalog(catalog: ManagerConfigurationCatalogDto): String? =
        ManagerContractValidationCore.validateConfigurationCatalog(
            ConfigurationCatalogValidationInput(
                schemaVersion = catalog.schemaVersion,
                sourceRepo = catalog.sourceRepo,
                branch = catalog.branch,
                generatedAt = catalog.generatedAt,
                files = catalog.files.map { entry ->
                    ConfigurationCatalogEntryValidationInput(
                        schemaVersion = entry.schemaVersion,
                        path = entry.path,
                        name = entry.name,
                        sha = entry.sha,
                        size = entry.size,
                        updatedAt = entry.updatedAt,
                    )
                },
            ),
        )

    fun validateConfigurationCatalogEntry(entry: ManagerConfigurationCatalogEntryDto): String? =
        ManagerContractValidationCore.validateConfigurationCatalogEntry(
            ConfigurationCatalogEntryValidationInput(
                schemaVersion = entry.schemaVersion,
                path = entry.path,
                name = entry.name,
                sha = entry.sha,
                size = entry.size,
                updatedAt = entry.updatedAt,
            ),
        )

    fun validateLogExportResult(result: ManagerLogExportResultDto): String? =
        ManagerContractValidationCore.validateLogExportResult(
            io.github.magisk317.mipush.manager.LogExportResultValidationInput(
                schemaVersion = result.schemaVersion,
                success = result.success,
                details = result.details,
                descriptorPresent = result.parcelFileDescriptor != null,
            ),
        )


    fun validateRuntimePreferences(snapshot: ManagerRuntimePreferencesDto): String? =
        ManagerConfigurationContractValidation.validateRuntimePreferences(snapshot)

    fun validateManagerMigrationSnapshot(snapshot: ManagerMigrationSnapshotDto): String? =
        ManagerConfigurationContractValidation.validateManagerMigrationSnapshot(snapshot)

    fun validateConfigurationUploadRequest(request: ManagerConfigurationUploadRequestDto): String? =
        ManagerConfigurationContractValidation.validateConfigurationUploadRequest(request)

    fun validateConfigurationUploadResult(result: ManagerConfigurationUploadResultDto): String? =
        ManagerConfigurationContractValidation.validateConfigurationUploadResult(result)

    fun validateWriteRequest(request: ManagerWriteRequestDto): String? =
        ManagerWriteContractValidation.validateWriteRequest(request)

    fun validateWriteResult(result: ManagerWriteResultDto): String? =
        ManagerWriteContractValidation.validateWriteResult(result)

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
