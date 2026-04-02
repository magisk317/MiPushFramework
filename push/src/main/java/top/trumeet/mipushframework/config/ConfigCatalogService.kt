package top.trumeet.mipushframework.config

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Singleton
class ConfigCatalogService @Inject constructor() {
    companion object {
        const val REMOTE_REPOSITORY = "magisk317/MiPushConfigurations"
        const val REMOTE_BRANCH = "dev"
        private const val USER_AGENT = "MiPushFramework/ConfigSync"
        private const val BASE_RAW_URL = "https://raw.githubusercontent.com/$REMOTE_REPOSITORY/$REMOTE_BRANCH"
        private const val INDEX_PATH = "_meta/config-index.json"
    }

    private val json = Json { ignoreUnknownKeys = true }

    suspend fun fetchCatalog(): RemoteConfigCatalog = withContext(Dispatchers.IO) {
        val text = fetchText("$BASE_RAW_URL/$INDEX_PATH")
        json.decodeFromString(RemoteConfigCatalog.serializer(), text)
    }

    suspend fun fetchRemoteFile(path: String): String = withContext(Dispatchers.IO) {
        fetchText("$BASE_RAW_URL/${encodePath(path)}")
    }

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
