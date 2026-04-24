package io.github.magisk317.mipush.runtime.core.config

data class ConfigRemoteSource(
    val repository: String = ConfigCatalogService.REMOTE_REPOSITORY,
    val branch: String = ConfigCatalogService.REMOTE_BRANCH,
) {
    val displayName: String get() = "$repository@$branch"
    val cacheKey: String get() = displayName
    val baseRawUrl: String get() = "https://raw.githubusercontent.com/$repository/$branch"
}
