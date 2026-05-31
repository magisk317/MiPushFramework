package io.github.magisk317.mipush.config

import android.net.Uri
import io.github.magisk317.mipush.utils.ConfigListItem as CoreConfigListItem
import io.github.magisk317.mipush.utils.LocalConfigSummary
import io.github.magisk317.mipush.utils.RemoteConfigCatalog as CoreRemoteConfigCatalog
import io.github.magisk317.mipush.utils.RemoteConfigFile as CoreRemoteConfigFile

// --- Types that stay in xmsf (depend on android.net.Uri or LocalConfigFile) ---

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

/**
 * Converts this platform-specific [LocalConfigFile] (with [Uri]) to a
 * platform-independent [LocalConfigSummary] suitable for use in :core logic.
 */
fun LocalConfigFile.toSummary(): LocalConfigSummary = LocalConfigSummary(
    path = path,
    name = name,
    sha = sha,
    size = size,
    lastModified = lastModified,
    isValid = isValid,
    validationError = validationError,
)

/**
 * [ConfigListItem] is now a typealias to the platform-independent version in :core.
 * The core version uses [LocalConfigSummary] instead of [LocalConfigFile].
 * Use [LocalConfigFile.toSummary] to convert when building list items.
 */
@Deprecated(
    "Moved to :core module",
    ReplaceWith("io.github.magisk317.mipush.utils.ConfigListItem")
)
typealias ConfigListItem = CoreConfigListItem

data class ConfigListSnapshot(
    val catalog: CoreRemoteConfigCatalog? = null,
    val items: List<CoreConfigListItem> = emptyList(),
    val remoteError: String? = null,
)

data class ConfigEditorSnapshot(
    val path: String,
    val local: io.github.magisk317.mipush.utils.ConfigDocumentContent? = null,
    val remote: io.github.magisk317.mipush.utils.ConfigDocumentContent? = null,
    val remoteMeta: CoreRemoteConfigFile? = null,
    val localMeta: LocalConfigFile? = null,
    val remoteError: String? = null,
)

// --- Typealiases for types moved to :core (backward compatibility) ---

@Deprecated(
    "Moved to :core module",
    ReplaceWith("io.github.magisk317.mipush.utils.RemoteConfigCatalog")
)
typealias RemoteConfigCatalog = CoreRemoteConfigCatalog

@Deprecated(
    "Moved to :core module",
    ReplaceWith("io.github.magisk317.mipush.utils.RemoteConfigFile")
)
typealias RemoteConfigFile = CoreRemoteConfigFile

@Deprecated(
    "Moved to :core module",
    ReplaceWith("io.github.magisk317.mipush.utils.ConfigSyncStatus")
)
typealias ConfigSyncStatus = io.github.magisk317.mipush.utils.ConfigSyncStatus

@Deprecated(
    "Moved to :core module",
    ReplaceWith("io.github.magisk317.mipush.utils.ConfigContentSource")
)
typealias ConfigContentSource = io.github.magisk317.mipush.utils.ConfigContentSource

@Deprecated(
    "Moved to :core module",
    ReplaceWith("io.github.magisk317.mipush.utils.JsonValidationResult")
)
typealias JsonValidationResult = io.github.magisk317.mipush.utils.JsonValidationResult

@Deprecated(
    "Moved to :core module",
    ReplaceWith("io.github.magisk317.mipush.utils.ConfigDocumentContent")
)
typealias ConfigDocumentContent = io.github.magisk317.mipush.utils.ConfigDocumentContent

@Deprecated(
    "Moved to :core module",
    ReplaceWith("io.github.magisk317.mipush.utils.ConfigSyncRecord")
)
typealias ConfigSyncRecord = io.github.magisk317.mipush.utils.ConfigSyncRecord

@Deprecated(
    "Moved to :core module",
    ReplaceWith("io.github.magisk317.mipush.utils.ConfigSyncState")
)
typealias ConfigSyncState = io.github.magisk317.mipush.utils.ConfigSyncState
