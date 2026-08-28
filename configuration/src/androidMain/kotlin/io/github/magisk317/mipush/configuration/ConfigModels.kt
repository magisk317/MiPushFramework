package io.github.magisk317.mipush.configuration

import android.net.Uri
import io.github.magisk317.mipush.core.configuration.LocalConfigSummary
import io.github.magisk317.mipush.core.configuration.RemoteConfigFile

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

data class ConfigEditorSnapshot(
    val path: String,
    val local: io.github.magisk317.mipush.core.configuration.ConfigDocumentContent? = null,
    val remote: io.github.magisk317.mipush.core.configuration.ConfigDocumentContent? = null,
    val remoteMeta: RemoteConfigFile? = null,
    val localMeta: LocalConfigFile? = null,
    val remoteError: String? = null,
)
