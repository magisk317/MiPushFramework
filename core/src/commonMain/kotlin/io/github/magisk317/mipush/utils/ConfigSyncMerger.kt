package io.github.magisk317.mipush.utils

/**
 * Pure-logic config sync merge utilities.
 * All functions operate on platform-independent types only (no Android dependencies).
 */

/**
 * Merges remote and local config file lists into a unified list of [ConfigListItem] entries,
 * determining the sync status of each entry based on local and remote SHA comparison.
 *
 * @param remoteFiles list of remote config files from the catalog
 * @param localFiles list of local config file summaries (platform-independent)
 * @param syncRecords kept for call-site compatibility; local files are treated as authoritative
 * @return sorted list of merged config list items with computed sync status
 */
@Suppress("UNUSED_PARAMETER")
fun mergeConfigEntries(
    remoteFiles: List<RemoteConfigFile>,
    localFiles: List<LocalConfigSummary>,
    syncRecords: Map<String, ConfigSyncRecord>,
): List<ConfigListItem> {
    val remoteByPath = remoteFiles.associateBy { it.path }
    val localByPath = localFiles.associateBy { it.path }
    return (remoteByPath.keys + localByPath.keys)
        .sorted()
        .map { path ->
            val remote = remoteByPath[path]
            val local = localByPath[path]
            ConfigListItem(
                path = path,
                displayName = local?.name ?: remote?.name ?: path.removeSuffix(".json"),
                status = determineStatus(local, remote),
                local = local,
                remote = remote,
            )
        }
}

private fun determineStatus(
    local: LocalConfigSummary?,
    remote: RemoteConfigFile?,
): ConfigSyncStatus {
    return when {
        local != null && !local.isValid -> ConfigSyncStatus.INVALID_LOCAL
        local == null && remote != null -> ConfigSyncStatus.REMOTE_ONLY
        local != null && remote == null -> ConfigSyncStatus.LOCAL_ONLY
        local != null && remote != null && local.sha == remote.sha -> ConfigSyncStatus.IN_SYNC
        local != null && remote != null -> ConfigSyncStatus.LOCAL_OVERRIDE
        else -> ConfigSyncStatus.LOCAL_ONLY
    }
}

/**
 * Guesses the config file path for a given package name by looking for an exact match
 * (`packageName.json`) or a prefix match (`packageName_*.json`) in the available paths.
 *
 * @param packageName the Android package name to search for
 * @param paths collection of available config file paths
 * @return the matching path, or null if no match is found
 */
fun guessPackageConfigPath(packageName: String, paths: Collection<String>): String? {
    val direct = "$packageName.json"
    val prefix = "${packageName}_"
    return paths
        .sorted()
        .firstOrNull { path -> path == direct || path.startsWith(prefix) }
}
