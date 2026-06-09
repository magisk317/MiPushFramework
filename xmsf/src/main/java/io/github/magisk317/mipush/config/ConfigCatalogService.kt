package io.github.magisk317.mipush.config

import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.data.dataStore
import io.github.magisk317.mipush.utils.ConfigDefaults
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.utils.RemoteConfigCatalog
import io.github.magisk317.mipush.utils.ConfigRemoteSource

class ConfigCatalogService constructor(
    private val preferenceRepository: PreferenceRepository,
) {
    companion object {
        const val REMOTE_REPOSITORY = ConfigDefaults.REMOTE_REPOSITORY
        const val REMOTE_BRANCH = ConfigDefaults.REMOTE_BRANCH
        private const val USER_AGENT = "MiPushFramework/ConfigSync"
        private const val INDEX_PATH = "_meta/config-index.json"
    }

    constructor() : this(PreferenceRepository(Utils.getApplication()!!.dataStore))

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchCatalog(source: ConfigRemoteSource): RemoteConfigCatalog = withContext(Dispatchers.IO) {
        try {
            val text = fetchText(source.rawUrl(INDEX_PATH))
            json.decodeFromString(RemoteConfigCatalog.serializer(), text)
        } catch (e: IOException) {
            // Fallback for AndroidNotifyIconAdapt which does not use catalog.json
            val fallbackPaths = listOf(
                "APP/NotifyIconsSupportConfig.json",
                "OS/ColorOS/NotifyIconsSupportConfig.json",
                "OS/MIUI/NotifyIconsSupportConfig.json"
            )
            val isAndroidNotifyIconAdapt = try {
                fetchText(source.rawUrl(fallbackPaths.first())).isNotEmpty()
            } catch (_: Exception) { false }
            
            if (isAndroidNotifyIconAdapt) {
                RemoteConfigCatalog(
                    sourceRepo = source.repository,
                    branch = source.branch,
                    generatedAt = "1970-01-01T00:00:00Z",
                    files = fallbackPaths.map {
                        io.github.magisk317.mipush.utils.RemoteConfigFile(
                            path = it,
                            name = it.substringAfterLast("/"),
                            sha = "virtual_fankes_repo",
                            size = 1000,
                            updatedAt = "1970-01-01T00:00:00Z"
                        )
                    }
                )
            } else {
                throw e
            }
        }
    }

    suspend fun fetchRemoteFile(source: ConfigRemoteSource, path: String): String = withContext(Dispatchers.IO) {
        fetchText(source.rawUrl(encodePath(path)))
    }

    suspend fun getRemoteSource(): ConfigRemoteSource = ConfigRemoteSource(
        repository = preferenceRepository.configRemoteRepository.first(),
        branch = preferenceRepository.configRemoteBranch.first(),
        accelerator = preferenceRepository.configRemoteAccelerator.first(),
    )

    suspend fun getIconRemoteSource(): ConfigRemoteSource = ConfigRemoteSource(
        repository = preferenceRepository.iconRemoteRepository.first(),
        branch = preferenceRepository.iconRemoteBranch.first(),
        accelerator = preferenceRepository.iconRemoteAccelerator.first(),
    )

    private fun fetchText(urlString: String): String {
        val connection = (URL(urlString).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 10_000
            setRequestProperty("Accept", "application/json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
            if (code !in 200..299) {
                throw IOException("HTTP $code ${connection.responseMessage}: $body")
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun encodePath(path: String): String {
        return path.split('/')
            .joinToString("/") { segment ->
                URLEncoder.encode(segment, StandardCharsets.UTF_8.toString()).replace("+", "%20")
            }
    }
}
