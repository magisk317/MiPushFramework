package io.github.magisk317.mipush.manager.configuration

sealed interface ConfigurationCatalogComparison {
    data object Matched : ConfigurationCatalogComparison
    data class Mismatched(val fields: List<String>) : ConfigurationCatalogComparison
    data class Unavailable(val status: ConfigurationCatalogReadStatus) : ConfigurationCatalogComparison
}

class ComparingConfigurationCatalogSource(
    private val remoteSource: RemoteConfigurationCatalogSource,
    private val enableRemoteCompare: Boolean = false,
) {
    /** Primary path still uses the existing config sync gateway/local cache; remote is comparison only. */
    suspend fun compareRemote(primary: ConfigurationCatalogSnapshot): ConfigurationCatalogComparison {
        if (!enableRemoteCompare) return ConfigurationCatalogComparison.Matched
        return when (val remote = remoteSource.load()) {
            is ConfigurationCatalogReadResult.Available -> compareCatalogs(primary, remote.value)
            is ConfigurationCatalogReadResult.Unavailable ->
                ConfigurationCatalogComparison.Unavailable(remote.status)
        }
    }
}

private fun compareCatalogs(
    primary: ConfigurationCatalogSnapshot,
    remote: ConfigurationCatalogSnapshot,
): ConfigurationCatalogComparison {
    val fields = mutableListOf<String>()
    if (primary.sourceRepo != remote.sourceRepo) fields += "sourceRepo"
    if (primary.branch != remote.branch) fields += "branch"
    if (primary.files.map { it.path } != remote.files.map { it.path }) fields += "paths"
    val primaryByPath = primary.files.associateBy { it.path }
    remote.files.forEach { right ->
        val left = primaryByPath[right.path] ?: return@forEach
        if (left.sha != right.sha) fields += "sha"
        if (left.size != right.size) fields += "size"
    }
    return if (fields.isEmpty()) {
        ConfigurationCatalogComparison.Matched
    } else {
        ConfigurationCatalogComparison.Mismatched(fields.distinct().sorted())
    }
}

