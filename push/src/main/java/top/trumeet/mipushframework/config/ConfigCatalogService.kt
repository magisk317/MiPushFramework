package top.trumeet.mipushframework.config

import com.magisk317.data.PreferenceRepository
import com.magisk317.data.dataStore
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import top.trumeet.common.utils.Utils

@Singleton
class ConfigCatalogService @Inject constructor(
    private val preferenceRepository: PreferenceRepository,
) {
    companion object {
        const val REMOTE_REPOSITORY = "magisk317/MiPushConfigurations"
        const val REMOTE_BRANCH = "dev"
        private const val USER_AGENT = "MiPushFramework/ConfigSync"
        private const val INDEX_PATH = "_meta/config-index.json"
    }

    constructor() : this(PreferenceRepository(Utils.getApplication()!!.dataStore))

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchCatalog(): RemoteConfigCatalog = withContext(Dispatchers.IO) {
        val source = getRemoteSource()
        val text = fetchText("${source.baseRawUrl}/$INDEX_PATH")
        json.decodeFromString(RemoteConfigCatalog.serializer(), text)
    }

    suspend fun fetchRemoteFile(path: String): String = withContext(Dispatchers.IO) {
        val source = getRemoteSource()
        fetchText("${source.baseRawUrl}/${encodePath(path)}")
    }

    suspend fun getRemoteSource(): ConfigRemoteSource = ConfigRemoteSource(
        repository = preferenceRepository.configRemoteRepository.first(),
        branch = preferenceRepository.configRemoteBranch.first(),
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
