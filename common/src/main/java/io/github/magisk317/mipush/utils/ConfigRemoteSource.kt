package io.github.magisk317.mipush.utils

data class ConfigRemoteSource(
    val repository: String = ConfigDefaults.REMOTE_REPOSITORY,
    val branch: String = ConfigDefaults.REMOTE_BRANCH,
    val accelerator: String = ConfigDefaults.REMOTE_ACCELERATOR,
) {
    val displayName: String get() = "$repository@$branch"
    val cacheKey: String get() = displayName
    val baseRawUrl: String get() = "https://raw.githubusercontent.com/$repository/$branch"

    fun rawUrl(path: String): String {
        val originalUrl = "$baseRawUrl/$path"
        val trimmedAccelerator = accelerator.trim().trimEnd('/')
        if (trimmedAccelerator.isEmpty()) return originalUrl
        return when {
            "{url}" in trimmedAccelerator -> trimmedAccelerator.replace("{url}", originalUrl)
            "%s" in trimmedAccelerator -> trimmedAccelerator.replace("%s", originalUrl)
            else -> "$trimmedAccelerator/$originalUrl"
        }
    }
}
