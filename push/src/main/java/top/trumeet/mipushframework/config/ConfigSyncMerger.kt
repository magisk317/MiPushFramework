package top.trumeet.mipushframework.config

fun mergeConfigEntries(
    remoteFiles: List<RemoteConfigFile>,
    localFiles: List<LocalConfigFile>,
    syncRecords: Map<String, ConfigSyncRecord>,
): List<ConfigListItem> {
    val remoteByPath = remoteFiles.associateBy { it.path }
    val localByPath = localFiles.associateBy { it.path }
    return (remoteByPath.keys + localByPath.keys)
        .sorted()
        .map { path ->
            val remote = remoteByPath[path]
            val local = localByPath[path]
            val record = syncRecords[path]
            ConfigListItem(
                path = path,
                displayName = local?.name ?: remote?.name ?: path.removeSuffix(".json"),
                status = determineStatus(local, remote, record),
                local = local,
                remote = remote,
            )
        }
}

private fun determineStatus(
    local: LocalConfigFile?,
    remote: RemoteConfigFile?,
    record: ConfigSyncRecord?,
): ConfigSyncStatus {
    return when {
        local != null && !local.isValid -> ConfigSyncStatus.INVALID_LOCAL
        local == null && remote != null -> ConfigSyncStatus.REMOTE_ONLY
        local != null && remote == null -> ConfigSyncStatus.LOCAL_ONLY
        local != null && remote != null && local.sha == remote.sha -> ConfigSyncStatus.IN_SYNC
        local != null && remote != null && record != null && record.localSha != local.sha ->
            ConfigSyncStatus.MODIFIED_LOCAL

        local != null && remote != null -> ConfigSyncStatus.OUTDATED_LOCAL
        else -> ConfigSyncStatus.LOCAL_ONLY
    }
}

fun guessPackageConfigPath(packageName: String, paths: Collection<String>): String? {
    val direct = "$packageName.json"
    val prefix = "${packageName}_"
    return paths
        .sorted()
        .firstOrNull { path -> path == direct || path.startsWith(prefix) }
}
