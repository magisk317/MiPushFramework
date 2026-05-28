package io.github.magisk317.mipush.utils

import kotlinx.serialization.Serializable

@Serializable
data class RemoteConfigCatalog(
    val sourceRepo: String,
    val branch: String,
    val generatedAt: String,
    val files: List<RemoteConfigFile>,
)

@Serializable
data class RemoteConfigFile(
    val path: String,
    val name: String,
    val sha: String,
    val size: Int,
    val updatedAt: String,
)

enum class ConfigSyncStatus {
    IN_SYNC,
    REMOTE_ONLY,
    LOCAL_ONLY,
    OUTDATED_LOCAL,
    MODIFIED_LOCAL,
    INVALID_LOCAL,
}

enum class ConfigContentSource {
    LOCAL,
    REMOTE,
}

/**
 * Platform-independent summary of a local config file.
 * Contains all metadata needed for sync logic without requiring android.net.Uri.
 */
data class LocalConfigSummary(
    val path: String,
    val name: String,
    val sha: String,
    val size: Long,
    val lastModified: Long,
    val isValid: Boolean,
    val validationError: String? = null,
)

/**
 * Platform-independent representation of a config list entry.
 * Uses [LocalConfigSummary] for local file metadata (no Android dependencies).
 */
data class ConfigListItem(
    val path: String,
    val displayName: String,
    val status: ConfigSyncStatus,
    val local: LocalConfigSummary? = null,
    val remote: RemoteConfigFile? = null,
)

data class JsonValidationResult(
    val valid: Boolean,
    val formatted: String? = null,
    val errorMessage: String? = null,
    val line: Int? = null,
    val column: Int? = null,
)

data class ConfigDocumentContent(
    val rawText: String,
    val displayText: String,
    val validation: JsonValidationResult,
)

@Serializable
data class ConfigSyncRecord(
    val path: String,
    val remoteSha: String? = null,
    val localSha: String? = null,
    val syncedAt: Long = 0L,
)

@Serializable
data class ConfigSyncState(
    val directories: Map<String, Map<String, ConfigSyncRecord>> = emptyMap(),
    val cachedCatalogs: Map<String, RemoteConfigCatalog> = emptyMap(),
)
