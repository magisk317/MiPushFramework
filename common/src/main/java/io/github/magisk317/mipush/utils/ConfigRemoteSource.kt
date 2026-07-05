package io.github.magisk317.mipush.utils

import java.net.URI

data class ConfigRemoteSource(
    val repository: String = ConfigDefaults.REMOTE_REPOSITORY,
    val branch: String = ConfigDefaults.REMOTE_BRANCH,
    val accelerator: String = ConfigDefaults.REMOTE_ACCELERATOR,
) {
    private val remoteProject: RemoteProject get() = RemoteProject.parse(repository)

    val displayName: String get() = "${remoteProject.displayRepository}@$branch"
    val cacheKey: String get() = "${remoteProject.cacheRepository}@$branch"
    val baseRawUrl: String get() = remoteProject.rawBaseUrl(branch)

    fun rawUrl(path: String): String {
        val originalUrl = "$baseRawUrl/${path.trimStart('/')}"
        val trimmedAccelerator = accelerator.trim().trimEnd('/')
        if (trimmedAccelerator.isEmpty()) return originalUrl
        return when {
            "{url}" in trimmedAccelerator -> trimmedAccelerator.replace("{url}", originalUrl)
            "%s" in trimmedAccelerator -> trimmedAccelerator.replace("%s", originalUrl)
            else -> "$trimmedAccelerator/$originalUrl"
        }
    }

    private enum class RemoteProvider {
        GITHUB,
        GITLAB,
    }

    private data class RemoteProject(
        val provider: RemoteProvider,
        val path: String,
    ) {
        val displayRepository: String
            get() = when (provider) {
                RemoteProvider.GITHUB -> path
                RemoteProvider.GITLAB -> "gitlab:$path"
            }

        val cacheRepository: String
            get() = when (provider) {
                RemoteProvider.GITHUB -> "github:$path"
                RemoteProvider.GITLAB -> "gitlab:$path"
            }

        fun rawBaseUrl(branch: String): String {
            val normalizedBranch = branch.trim()
            return when (provider) {
                RemoteProvider.GITHUB -> "https://raw.githubusercontent.com/$path/$normalizedBranch"
                RemoteProvider.GITLAB -> "https://gitlab.com/$path/-/raw/$normalizedBranch"
            }
        }

        companion object {
            fun parse(repository: String): RemoteProject {
                val trimmed = repository.trim().trimEnd('/')
                parseProjectUrl(trimmed)?.let { return it }

                val provider = when {
                    trimmed.startsWith("gitlab:", ignoreCase = true) -> RemoteProvider.GITLAB
                    else -> RemoteProvider.GITHUB
                }
                val path = when {
                    trimmed.startsWith("gitlab:", ignoreCase = true) -> trimmed.substringAfter(':')
                    trimmed.startsWith("github:", ignoreCase = true) -> trimmed.substringAfter(':')
                    else -> trimmed
                }
                return RemoteProject(provider, sanitizeProjectPath(path))
            }

            private fun parseProjectUrl(repository: String): RemoteProject? {
                val uri = runCatching { URI(repository) }.getOrNull() ?: return null
                val host = uri.host?.lowercase() ?: return null
                val provider = when (host.removePrefix("www.")) {
                    "github.com" -> RemoteProvider.GITHUB
                    "gitlab.com" -> RemoteProvider.GITLAB
                    else -> return null
                }
                return RemoteProject(provider, sanitizeProjectPath(uri.path.orEmpty()))
            }

            private fun sanitizeProjectPath(path: String): String {
                return path
                    .trim()
                    .trim('/')
                    .substringBefore("/-/")
                    .substringBefore("/tree/")
                    .substringBefore("/blob/")
                    .removeSuffix(".git")
            }
        }
    }
}
