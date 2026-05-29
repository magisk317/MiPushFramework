package io.github.magisk317.mipush.utils

data class ConfigRemoteSource(
    val repository: String = ConfigDefaults.REMOTE_REPOSITORY,
    val branch: String = ConfigDefaults.REMOTE_BRANCH,
) {
    val displayName: String get() = "$repository@$branch"
    val cacheKey: String get() = displayName
    val baseRawUrl: String get() = "https://raw.githubusercontent.com/$repository/$branch"
}
