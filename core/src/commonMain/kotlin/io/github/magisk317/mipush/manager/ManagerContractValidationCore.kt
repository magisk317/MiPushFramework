package io.github.magisk317.mipush.manager

data class ConnectionSnapshotValidationInput(
    val schemaVersion: Int,
    val connectionState: String,
    val serverHost: String?,
    val serverIp: String?,
)

data class RuntimeEnvironmentValidationInput(
    val schemaVersion: Int,
    val imei: String?,
    val macAddress: String?,
    val xmppServerHost: String,
)

data class ApplicationQueryValidationInput(
    val schemaVersion: Int,
    val query: String,
    val filterMode: Int,
    val pageSize: Int,
    val pageToken: String?,
    val userId: Int,
    val negotiatedMaxPageSize: Int,
)

data class ApplicationSummaryValidationInput(
    val schemaVersion: Int,
    val packageName: String,
    val appName: String,
    val appNamePinYin: String,
    val userId: Int,
)

data class ApplicationStatsValidationInput(
    val schemaVersion: Int,
    val total: Int,
    val usingMiPush: Int,
    val notUsingMiPush: Int,
    val registered: Int,
    val notRegistered: Int,
)

data class ApplicationPageValidationInput(
    val schemaVersion: Int,
    val userId: Int,
    val itemUserIds: List<Int>,
    val summaries: List<ApplicationSummaryValidationInput>,
    val stats: ApplicationStatsValidationInput,
    val nextPageToken: String?,
    val negotiatedMaxPageSize: Int,
    val negotiatedMaxPayloadBytes: Int,
    val wireSize: ApplicationPageWireSizeInput,
)

data class ApplicationDetailValidationInput(
    val schemaVersion: Int,
    val packageName: String,
    val appName: String,
    val appNamePinYin: String,
    val userId: Int,
)

data class ApplicationDiagnosticsValidationInput(
    val schemaVersion: Int,
    val regSecCount: Int,
    val inferenceReason: String,
    val userId: Int,
)

data class EventQueryValidationInput(
    val schemaVersion: Int,
    val lastId: Long?,
    val pageSize: Int,
    val packageName: String,
    val query: String,
    val userId: Int,
    val negotiatedMaxPageSize: Int,
)

data class EventSummaryValidationInput(
    val schemaVersion: Int,
    val id: Long,
    val packageName: String,
    val configOptions: List<String>,
    val channel: String,
    val title: String,
    val content: String,
    val appName: String?,
    val info: String?,
    val payloadBytes: Int,
    val regSec: String?,
    val userId: Int,
)

data class EventPageValidationInput(
    val schemaVersion: Int,
    val summaries: List<EventSummaryValidationInput>,
    val negotiatedMaxPageSize: Int,
    val negotiatedMaxPayloadBytes: Int,
    val wireSize: List<EventSummaryWireSizeInput>,
)

data class NotificationChannelQueryValidationInput(
    val schemaVersion: Int,
    val packageName: String,
    val pageSize: Int,
    val pageToken: String?,
    val userId: Int,
    val negotiatedMaxPageSize: Int,
)

data class NotificationChannelSummaryValidationInput(
    val schemaVersion: Int,
    val id: String,
    val name: String,
    val groupId: String?,
    val description: String?,
)

data class NotificationChannelGroupValidationInput(
    val schemaVersion: Int,
    val id: String,
    val name: String,
)

data class NotificationChannelPageValidationInput(
    val schemaVersion: Int,
    val userId: Int,
    val items: List<NotificationChannelSummaryValidationInput>,
    val groups: List<NotificationChannelGroupValidationInput>,
    val nextPageToken: String?,
    val negotiatedMaxPageSize: Int,
    val negotiatedMaxPayloadBytes: Int,
    val wireSize: NotificationChannelPageWireSizeInput,
)

data class ConfigurationCatalogEntryValidationInput(
    val schemaVersion: Int,
    val path: String,
    val name: String,
    val sha: String,
    val size: Int,
    val updatedAt: String,
)

data class ConfigurationCatalogValidationInput(
    val schemaVersion: Int,
    val sourceRepo: String,
    val branch: String,
    val generatedAt: String,
    val files: List<ConfigurationCatalogEntryValidationInput>,
)

data class LogExportResultValidationInput(
    val schemaVersion: Int,
    val success: Boolean,
    val details: String,
    val descriptorPresent: Boolean,
)

data class PreferenceEntryValidationInput(
    val schemaVersion: Int,
    val key: String,
    val type: String,
    val value: String,
    val owner: String,
)

data class RuntimePreferencesValidationInput(
    val schemaVersion: Int,
    val entries: List<PreferenceEntryValidationInput>,
)

data class ConfigurationUploadRequestValidationInput(
    val schemaVersion: Int,
    val path: String,
    val contentLength: Int,
    val descriptorPresent: Boolean,
)

data class ConfigurationUploadResultValidationInput(
    val schemaVersion: Int,
    val details: String,
)

data class WriteRequestValidationInput(
    val schemaVersion: Int,
    val requestId: String,
    val operation: String,
    val packageName: String,
    val userId: Int,
    val eventId: Long?,
    val intArgument: Int,
    val argumentLength: Int,
)

data class WriteResultValidationInput(
    val schemaVersion: Int,
    val requestId: String,
    val status: String,
    val details: String,
)

/** All manager contract validation that can run without Android DTOs or Parcelable. */
object ManagerContractValidationCore {
    fun validateConnectionSnapshot(input: ConnectionSnapshotValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_connection_snapshot_schema"
        input.connectionState.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH -> "connection_state_too_long"
        input.serverHost?.length?.let { it > ManagerContractLimits.MAX_WIRE_STRING_LENGTH } == true -> "server_host_too_long"
        input.serverIp?.length?.let { it > ManagerContractLimits.MAX_WIRE_STRING_LENGTH } == true -> "server_ip_too_long"
        else -> null
    }

    fun validateRuntimeEnvironment(input: RuntimeEnvironmentValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_runtime_environment_schema"
        input.imei?.length?.let { it > ManagerContractLimits.MAX_WIRE_STRING_LENGTH } == true -> "imei_too_long"
        input.macAddress?.length?.let { it > ManagerContractLimits.MAX_WIRE_STRING_LENGTH } == true -> "mac_address_too_long"
        input.xmppServerHost.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH -> "xmpp_server_host_too_long"
        else -> null
    }

    fun validateApplicationQuery(input: ApplicationQueryValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_application_query_schema"
        input.negotiatedMaxPageSize !in 1..ManagerContractLimits.MAX_NEGOTIATED_PAGE_SIZE -> "invalid_negotiated_page_size"
        input.query.length > ManagerContractLimits.MAX_APPLICATION_QUERY_LENGTH -> "application_query_too_long"
        input.filterMode !in 0..3 -> "invalid_application_filter_mode"
        input.pageSize !in 1..input.negotiatedMaxPageSize -> "invalid_application_page_size"
        input.userId < 0 -> "invalid_application_user_id"
        input.pageToken?.length?.let { it > ManagerContractLimits.MAX_PAGE_TOKEN_LENGTH } == true ->
            "application_page_token_too_long"
        else -> null
    }

    fun validateApplicationPage(input: ApplicationPageValidationInput): String? {
        if (input.schemaVersion < 1) return "invalid_application_page_schema"
        if (input.userId < 0 || input.itemUserIds.any { it != input.userId }) return "invalid_application_user_id"
        if (input.negotiatedMaxPageSize !in 1..ManagerContractLimits.MAX_NEGOTIATED_PAGE_SIZE) {
            return "invalid_negotiated_page_size"
        }
        if (input.negotiatedMaxPayloadBytes !in 1..ManagerContractLimits.MAX_NEGOTIATED_PAYLOAD_BYTES) {
            return "invalid_negotiated_payload_bytes"
        }
        if (input.summaries.size > input.negotiatedMaxPageSize) return "too_many_application_page_items"
        if (input.nextPageToken?.length?.let { it > ManagerContractLimits.MAX_PAGE_TOKEN_LENGTH } == true) {
            return "application_next_page_token_too_long"
        }
        firstError(input.summaries.map(::validateApplicationSummary))?.let { return it }
        validateApplicationStats(input.stats)?.let { return it }
        return if (ManagerWireSizeCore.estimateApplicationPage(input.wireSize) > input.negotiatedMaxPayloadBytes) {
            "application_page_payload_too_large"
        } else {
            null
        }
    }

    fun validateApplicationSummary(input: ApplicationSummaryValidationInput): String? =
        if (input.userId < 0) {
            "invalid_application_user_id"
        } else {
            validateApplicationFields(
                input.schemaVersion,
                input.packageName,
                input.appName,
                input.appNamePinYin,
                "invalid_application_summary_schema",
            )
        }

    fun validateApplicationStats(input: ApplicationStatsValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_application_stats_schema"
        input.total < 0 || input.usingMiPush < 0 || input.notUsingMiPush < 0 ||
            input.registered < 0 || input.notRegistered < 0 -> "invalid_application_stats_count"
        input.usingMiPush.toLong() + input.notUsingMiPush.toLong() != input.total.toLong() ->
            "inconsistent_application_stats_total"
        input.registered.toLong() + input.notRegistered.toLong() != input.usingMiPush.toLong() ->
            "inconsistent_application_stats_registration"
        else -> null
    }

    fun validateApplicationDetail(input: ApplicationDetailValidationInput): String? =
        if (input.userId < 0) {
            "invalid_application_user_id"
        } else {
            validateApplicationFields(
                input.schemaVersion,
                input.packageName,
                input.appName,
                input.appNamePinYin,
                "invalid_application_detail_schema",
            )
        }

    fun validateApplicationDiagnostics(input: ApplicationDiagnosticsValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_application_diagnostics_schema"
        input.userId < 0 -> "invalid_application_user_id"
        input.regSecCount < 0 -> "invalid_application_diagnostics_reg_sec_count"
        input.inferenceReason.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH ->
            "application_diagnostics_inference_reason_too_long"
        else -> null
    }

    fun validatePackageName(packageName: String): String? = when {
        packageName.isBlank() || packageName.length > ManagerContractLimits.MAX_PACKAGE_NAME_LENGTH ->
            "invalid_application_package_name"
        packageName.any { !it.isLetterOrDigit() && it != '.' && it != '_' } ->
            "invalid_application_package_name"
        else -> null
    }

    fun validateDiagnosticsRequest(packageName: String, registeredType: Int): String? =
        validatePackageName(packageName) ?: if (registeredType !in 0..2) {
            "invalid_application_registered_type"
        } else {
            null
        }

    fun validateEventQuery(input: EventQueryValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_event_query_schema"
        input.negotiatedMaxPageSize !in 1..ManagerContractLimits.MAX_NEGOTIATED_PAGE_SIZE ->
            "invalid_negotiated_page_size"
        input.userId < 0 -> "invalid_event_user_id"
        input.pageSize !in 1..input.negotiatedMaxPageSize -> "invalid_event_page_size"
        input.query.length > ManagerContractLimits.MAX_APPLICATION_QUERY_LENGTH -> "event_query_too_long"
        input.packageName.isNotEmpty() && validatePackageName(input.packageName) != null ->
            "invalid_event_package_name"
        input.lastId != null && input.lastId < 0L -> "invalid_event_last_id"
        else -> null
    }

    fun validateEventSummary(input: EventSummaryValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_event_summary_schema"
        input.userId < 0 -> "invalid_event_user_id"
        input.id < 0L -> "invalid_event_id"
        input.packageName.isNotEmpty() && validatePackageName(input.packageName) != null ->
            "invalid_event_package_name"
        input.title.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH -> "event_title_too_long"
        input.content.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH -> "event_content_too_long"
        input.channel.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH -> "event_channel_too_long"
        input.appName?.length?.let { it > ManagerContractLimits.MAX_WIRE_STRING_LENGTH } == true -> "event_app_name_too_long"
        input.info?.length?.let { it > ManagerContractLimits.MAX_WIRE_STRING_LENGTH } == true -> "event_info_too_long"
        input.regSec?.length?.let { it > ManagerContractLimits.MAX_WIRE_STRING_LENGTH } == true -> "event_reg_sec_too_long"
        input.configOptions.size > ManagerContractLimits.MAX_EVENT_CONFIG_OPTION_COUNT -> "too_many_event_config_options"
        input.configOptions.any { it.length > ManagerContractLimits.MAX_EVENT_CONFIG_OPTION_LENGTH } ->
            "event_config_option_too_long"
        input.payloadBytes < 0 -> "invalid_event_payload_size"
        input.payloadBytes > ManagerContractLimits.MAX_EVENT_PAYLOAD_BYTES -> "event_payload_too_large"
        else -> null
    }

    fun validateEventPage(input: EventPageValidationInput): String? {
        if (input.schemaVersion < 1) return "invalid_event_page_schema"
        if (input.negotiatedMaxPageSize !in 1..ManagerContractLimits.MAX_NEGOTIATED_PAGE_SIZE) {
            return "invalid_negotiated_page_size"
        }
        if (input.negotiatedMaxPayloadBytes !in 1..ManagerContractLimits.MAX_NEGOTIATED_PAYLOAD_BYTES) {
            return "invalid_negotiated_payload_bytes"
        }
        if (input.summaries.size > input.negotiatedMaxPageSize) return "too_many_event_page_items"
        firstError(input.summaries.map(::validateEventSummary))?.let { return it }
        return if (ManagerWireSizeCore.estimateEventPage(input.wireSize) > input.negotiatedMaxPayloadBytes) {
            "event_page_payload_too_large"
        } else {
            null
        }
    }

    fun validateNotificationChannelQuery(input: NotificationChannelQueryValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_notification_channel_query_schema"
        input.negotiatedMaxPageSize !in 1..ManagerContractLimits.MAX_NEGOTIATED_PAGE_SIZE ->
            "invalid_negotiated_page_size"
        validatePackageName(input.packageName) != null -> "invalid_notification_channel_package_name"
        input.pageSize !in 1..input.negotiatedMaxPageSize -> "invalid_notification_channel_page_size"
        input.userId < 0 -> "invalid_notification_channel_user_id"
        input.pageToken?.length?.let { it > ManagerContractLimits.MAX_PAGE_TOKEN_LENGTH } == true ->
            "notification_channel_page_token_too_long"
        else -> null
    }

    fun validateNotificationChannelSummary(input: NotificationChannelSummaryValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_notification_channel_summary_schema"
        input.id.isBlank() || input.id.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH ->
            "invalid_notification_channel_id"
        input.name.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH -> "notification_channel_name_too_long"
        input.groupId?.length?.let { it > ManagerContractLimits.MAX_WIRE_STRING_LENGTH } == true ->
            "notification_channel_group_id_too_long"
        input.description?.length?.let { it > ManagerContractLimits.MAX_WIRE_STRING_LENGTH } == true ->
            "notification_channel_description_too_long"
        else -> null
    }

    fun validateNotificationChannelGroup(input: NotificationChannelGroupValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_notification_channel_group_schema"
        input.id.isBlank() || input.id.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH ->
            "invalid_notification_channel_group_id"
        input.name.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH -> "notification_channel_group_name_too_long"
        else -> null
    }

    fun validateNotificationChannelPage(input: NotificationChannelPageValidationInput): String? {
        if (input.schemaVersion < 1) return "invalid_notification_channel_page_schema"
        if (input.userId < 0) return "invalid_notification_channel_user_id"
        if (input.negotiatedMaxPageSize !in 1..ManagerContractLimits.MAX_NEGOTIATED_PAGE_SIZE) {
            return "invalid_negotiated_page_size"
        }
        if (input.items.size > input.negotiatedMaxPageSize) {
            return "too_many_notification_channel_page_items"
        }
        if (input.groups.size > ManagerContractLimits.MAX_NOTIFICATION_CHANNEL_GROUP_COUNT) {
            return "too_many_notification_channel_groups"
        }
        if (input.nextPageToken?.length?.let { it > ManagerContractLimits.MAX_PAGE_TOKEN_LENGTH } == true) {
            return "notification_channel_next_page_token_too_long"
        }
        if (input.negotiatedMaxPayloadBytes !in 1..ManagerContractLimits.MAX_NEGOTIATED_PAYLOAD_BYTES) {
            return "invalid_negotiated_payload_bytes"
        }
        firstError(input.items.map(::validateNotificationChannelSummary))?.let { return it }
        firstError(input.groups.map(::validateNotificationChannelGroup))?.let { return it }
        return if (ManagerWireSizeCore.estimateNotificationChannelPage(input.wireSize) > input.negotiatedMaxPayloadBytes) {
            "notification_channel_page_payload_too_large"
        } else {
            null
        }
    }

    fun validateConfigurationCatalog(input: ConfigurationCatalogValidationInput): String? {
        if (input.schemaVersion < 1) return "invalid_configuration_catalog_schema"
        if (input.sourceRepo.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH) return "configuration_source_repo_too_long"
        if (input.branch.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH) return "configuration_branch_too_long"
        if (input.generatedAt.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH) return "configuration_generated_at_too_long"
        if (input.files.size > ManagerContractLimits.MAX_CONFIGURATION_CATALOG_ITEM_COUNT) {
            return "too_many_configuration_catalog_items"
        }
        return firstError(input.files.map(::validateConfigurationCatalogEntry))
    }

    fun validateConfigurationCatalogEntry(input: ConfigurationCatalogEntryValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_configuration_catalog_entry_schema"
        input.path.isBlank() || input.path.length > ManagerContractLimits.MAX_CONFIGURATION_PATH_LENGTH ->
            "invalid_configuration_path"
        input.name.length > ManagerContractLimits.MAX_CONFIGURATION_NAME_LENGTH -> "configuration_name_too_long"
        input.sha.length > ManagerContractLimits.MAX_CONFIGURATION_SHA_LENGTH -> "configuration_sha_too_long"
        input.size < 0 -> "invalid_configuration_size"
        input.updatedAt.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH -> "configuration_updated_at_too_long"
        else -> null
    }

    fun validateLogExportResult(input: LogExportResultValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_log_export_result_schema"
        input.details.length > ManagerContractLimits.MAX_LOG_EXPORT_DETAILS_LENGTH -> "log_export_details_too_long"
        input.success && !input.descriptorPresent -> "log_export_missing_descriptor"
        else -> null
    }

    fun validateRuntimePreferences(input: RuntimePreferencesValidationInput): String? {
        if (input.schemaVersion < 1) return "invalid_runtime_preferences_schema"
        if (input.entries.size > ManagerContractLimits.MAX_PREFERENCE_ENTRY_COUNT) return "too_many_runtime_preferences"
        return firstError(input.entries.map { validatePreferenceEntry(it, requireRuntimeOwner = true) })
    }

    fun validateManagerMigrationSnapshot(schemaVersion: Int, entries: List<PreferenceEntryValidationInput>): String? {
        if (schemaVersion < 1) return "invalid_manager_migration_snapshot_schema"
        if (entries.size > ManagerContractLimits.MAX_PREFERENCE_ENTRY_COUNT) return "too_many_migration_preferences"
        return firstError(entries.map { validatePreferenceEntry(it, requireRuntimeOwner = false) })
    }

    fun validateConfigurationUploadRequest(input: ConfigurationUploadRequestValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_configuration_upload_request_schema"
        input.path.isBlank() || input.path.length > ManagerContractLimits.MAX_CONFIGURATION_PATH_LENGTH ->
            "invalid_configuration_upload_path"
        input.path.contains("..") || input.path.startsWith("/") -> "invalid_configuration_upload_path"
        input.contentLength !in 0..ManagerContractLimits.MAX_CONFIGURATION_UPLOAD_BYTES -> "invalid_configuration_upload_size"
        !input.descriptorPresent -> "configuration_upload_missing_descriptor"
        else -> null
    }

    fun validateConfigurationUploadResult(input: ConfigurationUploadResultValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_configuration_upload_result_schema"
        input.details.length > ManagerContractLimits.MAX_LOG_EXPORT_DETAILS_LENGTH -> "configuration_upload_details_too_long"
        else -> null
    }

    fun validateWriteRequest(input: WriteRequestValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_write_request_schema"
        input.requestId.isBlank() || input.requestId.length > ManagerContractLimits.MAX_WRITE_REQUEST_ID_LENGTH ->
            "invalid_write_request_id"
        input.operation.isBlank() || input.operation.length > ManagerContractLimits.MAX_WRITE_OPERATION_LENGTH ->
            "invalid_write_operation"
        input.argumentLength > ManagerContractLimits.MAX_WRITE_ARGUMENT_LENGTH -> "write_argument_too_long"
        input.packageName.isNotEmpty() && validatePackageName(input.packageName) != null -> "invalid_write_package_name"
        input.operation in OPERATIONS_REQUIRING_PACKAGE && validatePackageName(input.packageName) != null ->
            "write_package_name_required"
        input.operation in OPERATIONS_REQUIRING_EVENT_ID && (input.eventId == null || input.eventId <= 0L) ->
            "invalid_write_event_id"
        input.operation in OPERATIONS_REQUIRING_USER && input.userId < 0 -> "invalid_write_user_id"
        input.operation == WRITE_OP_GRANT_SILENT_PERMISSIONS && input.intArgument !in GRANT_SILENT_PERMISSION_USER_IDS ->
            "invalid_grant_user_id"
        input.eventId != null && input.eventId < 0L -> "invalid_write_event_id"
        else -> null
    }

    fun validateWriteResult(input: WriteResultValidationInput): String? = when {
        input.schemaVersion < 1 -> "invalid_write_result_schema"
        input.requestId.isBlank() || input.requestId.length > ManagerContractLimits.MAX_WRITE_REQUEST_ID_LENGTH ->
            "invalid_write_result_request_id"
        input.status !in WRITE_STATUSES -> "invalid_write_status"
        input.details.length > ManagerContractLimits.MAX_WRITE_DETAILS_LENGTH -> "write_details_too_long"
        else -> null
    }

    private fun validatePreferenceEntry(input: PreferenceEntryValidationInput, requireRuntimeOwner: Boolean): String? = when {
        input.schemaVersion < 1 -> "invalid_preference_entry_schema"
        input.key.isBlank() || input.key.length > ManagerContractLimits.MAX_PREFERENCE_KEY_LENGTH -> "invalid_preference_key"
        input.value.length > ManagerContractLimits.MAX_PREFERENCE_VALUE_LENGTH -> "preference_value_too_long"
        input.type !in PREFERENCE_TYPES -> "invalid_preference_type"
        requireRuntimeOwner && input.owner != "runtime" -> "preference_not_runtime_owned"
        !requireRuntimeOwner && input.owner != "manager" -> "preference_not_manager_owned"
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
        validatePackageName(packageName) != null -> "invalid_application_package_name"
        appName.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH -> "application_name_too_long"
        appNamePinYin.length > ManagerContractLimits.MAX_WIRE_STRING_LENGTH -> "application_name_pinyin_too_long"
        else -> null
    }

    private fun <T> firstError(errors: List<T?>): T? = errors.firstOrNull { it != null }

    private val PREFERENCE_TYPES = setOf("string", "boolean", "int", "long", "float")
    private val WRITE_STATUSES = setOf("success", "failed", "unsupported", "duplicate")
    private val GRANT_SILENT_PERMISSION_USER_IDS = setOf(0, 999, -1)
    private val OPERATIONS_REQUIRING_PACKAGE = setOf(
        "update_application", "launch_target_force_register", "delete_event", "restore_event",
        "mock_message", "query_usage_stats", "delete_notification_channel", "zygisk_force_stop",
        "get_event_content", "get_event_json",
    )
    private val OPERATIONS_REQUIRING_EVENT_ID = setOf(
        "delete_event", "restore_event", "mock_message", "get_event_content", "get_event_json",
    )
    private val OPERATIONS_REQUIRING_USER = OPERATIONS_REQUIRING_EVENT_ID
    private const val WRITE_OP_GRANT_SILENT_PERMISSIONS = "grant_silent_permissions"
}
