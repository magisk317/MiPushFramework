package io.github.magisk317.mipush.manager.api

/** Internal preference ownership and configuration-upload validation domain. */
internal object ManagerConfigurationContractValidation {
    fun validateRuntimePreferences(snapshot: ManagerRuntimePreferencesDto): String? {
        if (snapshot.schemaVersion < 1) return "invalid_runtime_preferences_schema"
        if (snapshot.entries.size > ManagerProtocol.MAX_PREFERENCE_ENTRY_COUNT) {
            return "too_many_runtime_preferences"
        }
        snapshot.entries.forEach { entry ->
            validatePreferenceEntry(entry, requireRuntimeOwner = true)?.let { return it }
        }
        return null
    }

    fun validateManagerMigrationSnapshot(snapshot: ManagerMigrationSnapshotDto): String? {
        if (snapshot.schemaVersion < 1) return "invalid_manager_migration_snapshot_schema"
        if (snapshot.entries.size > ManagerProtocol.MAX_PREFERENCE_ENTRY_COUNT) {
            return "too_many_migration_preferences"
        }
        snapshot.entries.forEach { entry ->
            validatePreferenceEntry(entry, requireRuntimeOwner = false)?.let { return it }
        }
        return null
    }

    fun validateConfigurationUploadRequest(request: ManagerConfigurationUploadRequestDto): String? = when {
        request.schemaVersion < 1 -> "invalid_configuration_upload_request_schema"
        request.path.isBlank() || request.path.length > ManagerProtocol.MAX_CONFIGURATION_PATH_LENGTH ->
            "invalid_configuration_upload_path"
        request.path.contains("..") || request.path.startsWith("/") ->
            "invalid_configuration_upload_path"
        request.contentLength !in 0..ManagerProtocol.MAX_CONFIGURATION_UPLOAD_BYTES ->
            "invalid_configuration_upload_size"
        request.parcelFileDescriptor == null -> "configuration_upload_missing_descriptor"
        else -> null
    }

    fun validateConfigurationUploadResult(result: ManagerConfigurationUploadResultDto): String? = when {
        result.schemaVersion < 1 -> "invalid_configuration_upload_result_schema"
        result.details.length > ManagerProtocol.MAX_LOG_EXPORT_DETAILS_LENGTH ->
            "configuration_upload_details_too_long"
        else -> null
    }

    private fun validatePreferenceEntry(
        entry: ManagerPreferenceEntryDto,
        requireRuntimeOwner: Boolean,
    ): String? = when {
        entry.schemaVersion < 1 -> "invalid_preference_entry_schema"
        entry.key.isBlank() || entry.key.length > ManagerProtocol.MAX_PREFERENCE_KEY_LENGTH ->
            "invalid_preference_key"
        entry.value.length > ManagerProtocol.MAX_PREFERENCE_VALUE_LENGTH -> "preference_value_too_long"
        entry.type !in setOf("string", "boolean", "int", "long", "float") ->
            "invalid_preference_type"
        requireRuntimeOwner && entry.owner != "runtime" -> "preference_not_runtime_owned"
        !requireRuntimeOwner && entry.owner != "manager" -> "preference_not_manager_owned"
        else -> null
    }
}
