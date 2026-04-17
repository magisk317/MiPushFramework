package io.github.magisk317.mipush.config

import android.net.Uri
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

data class LocalConfigFile(
    val path: String,
    val name: String,
    val uri: Uri?,
    val sha: String,
    val size: Long,
    val lastModified: Long,
    val isValid: Boolean,
    val validationError: String? = null,
)

data class ConfigListItem(
    val path: String,
    val displayName: String,
    val status: ConfigSyncStatus,
    val local: LocalConfigFile? = null,
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

data class ConfigListSnapshot(
    val catalog: RemoteConfigCatalog? = null,
    val items: List<ConfigListItem> = emptyList(),
    val remoteError: String? = null,
)

data class ConfigEditorSnapshot(
    val path: String,
    val local: ConfigDocumentContent? = null,
    val remote: ConfigDocumentContent? = null,
    val remoteMeta: RemoteConfigFile? = null,
    val localMeta: LocalConfigFile? = null,
    val remoteError: String? = null,
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
