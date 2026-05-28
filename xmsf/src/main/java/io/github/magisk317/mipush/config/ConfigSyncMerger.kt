package io.github.magisk317.mipush.config

import io.github.magisk317.mipush.utils.ConfigListItem as CoreConfigListItem
import io.github.magisk317.mipush.utils.ConfigSyncRecord
import io.github.magisk317.mipush.utils.RemoteConfigFile
import io.github.magisk317.mipush.utils.mergeConfigEntries as coreMergeConfigEntries
import io.github.magisk317.mipush.utils.guessPackageConfigPath as coreGuessPackageConfigPath

/**
 * xmsf wrapper for [coreMergeConfigEntries] that accepts [LocalConfigFile] (with Android Uri)
 * and converts to [LocalConfigSummary][io.github.magisk317.mipush.utils.LocalConfigSummary]
 * before delegating to the platform-independent core implementation.
 */
fun mergeConfigEntries(
    remoteFiles: List<RemoteConfigFile>,
    localFiles: List<LocalConfigFile>,
    syncRecords: Map<String, ConfigSyncRecord>,
): List<CoreConfigListItem> {
    return coreMergeConfigEntries(
        remoteFiles = remoteFiles,
        localFiles = localFiles.map { it.toSummary() },
        syncRecords = syncRecords,
    )
}

/**
 * xmsf wrapper for [coreGuessPackageConfigPath] — delegates directly to core
 * since the function signature is already platform-independent.
 */
fun guessPackageConfigPath(packageName: String, paths: Collection<String>): String? {
    return coreGuessPackageConfigPath(packageName, paths)
}
