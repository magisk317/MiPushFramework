package io.github.magisk317.mipush.configuration

import android.net.Uri
import io.github.magisk317.mipush.utils.ConfigListItem
import io.github.magisk317.mipush.utils.LocalConfigSummary
import io.github.magisk317.mipush.utils.RemoteConfigCatalog
import io.github.magisk317.mipush.utils.RemoteConfigFile

// --- Types that stay in :configuration (depend on android.net.Uri) ---

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

data class ConfigListSnapshot(
    val catalog: RemoteConfigCatalog? = null,
    val items: List<ConfigListItem> = emptyList(),
    val remoteError: String? = null,
)

data class ConfigEditorSnapshot(
    val path: String,
    val local: io.github.magisk317.mipush.utils.ConfigDocumentContent? = null,
    val remote: io.github.magisk317.mipush.utils.ConfigDocumentContent? = null,
    val remoteMeta: RemoteConfigFile? = null,
    val localMeta: LocalConfigFile? = null,
    val remoteError: String? = null,
)
