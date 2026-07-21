package io.github.magisk317.mipush.manager.configuration

import io.github.magisk317.mipush.manager.api.ManagerConfigurationCatalogDto
import io.github.magisk317.mipush.manager.api.ManagerConfigurationCatalogEntryDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import io.github.magisk317.mipush.utils.RemoteConfigCatalog
import io.github.magisk317.mipush.utils.RemoteConfigFile
import kotlinx.coroutines.CancellationException

data class ConfigurationCatalogSnapshot(
    val sourceRepo: String,
    val branch: String,
    val generatedAt: String,
    val files: List<ConfigurationCatalogEntry>,
)

data class ConfigurationCatalogEntry(
    val path: String,
    val name: String,
    val sha: String,
    val size: Int,
    val updatedAt: String,
)

sealed interface ConfigurationCatalogReadResult<out T> {
    data class Available<T>(val value: T) : ConfigurationCatalogReadResult<T>
    data class Unavailable(val status: ConfigurationCatalogReadStatus) : ConfigurationCatalogReadResult<Nothing>
}

enum class ConfigurationCatalogReadStatus {
    UNSUPPORTED,
    DISCONNECTED,
    BINDING,
    RUNTIME_MISSING,
    PERMISSION_DENIED,
    TIMED_OUT,
    INCOMPATIBLE,
    TEMPORARILY_DISCONNECTED,
    FAILED,
}

sealed interface ConfigurationCatalogComparison {
    data object Matched : ConfigurationCatalogComparison
    data class Mismatched(val fields: List<String>) : ConfigurationCatalogComparison
    data class Unavailable(val status: ConfigurationCatalogReadStatus) : ConfigurationCatalogComparison
}

class RemoteConfigurationCatalogSource internal constructor(
    private val loader: suspend () -> ManagerRuntimeResult<ManagerConfigurationCatalogDto>,
) {
    constructor(client: ManagerRuntimeClient) : this(client::getConfigurationCatalog)

    suspend fun load(): ConfigurationCatalogReadResult<ConfigurationCatalogSnapshot> = try {
        when (val result = loader()) {
            is ManagerRuntimeResult.Success -> ConfigurationCatalogReadResult.Available(result.value.toSnapshot())
            is ManagerRuntimeResult.Unsupported ->
                ConfigurationCatalogReadResult.Unavailable(ConfigurationCatalogReadStatus.UNSUPPORTED)
            is ManagerRuntimeResult.Unavailable ->
                ConfigurationCatalogReadResult.Unavailable(result.availability.toStatus())
            is ManagerRuntimeResult.Failed ->
                ConfigurationCatalogReadResult.Unavailable(ConfigurationCatalogReadStatus.FAILED)
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: RuntimeException) {
        ConfigurationCatalogReadResult.Unavailable(ConfigurationCatalogReadStatus.FAILED)
    }
}

class ComparingConfigurationCatalogSource(
    private val remoteSource: RemoteConfigurationCatalogSource,
) {
    /** Primary path still uses the existing config sync gateway/local cache; remote is comparison only. */
    suspend fun compareRemote(primary: ConfigurationCatalogSnapshot): ConfigurationCatalogComparison =
        when (val remote = remoteSource.load()) {
            is ConfigurationCatalogReadResult.Available -> compareCatalogs(primary, remote.value)
            is ConfigurationCatalogReadResult.Unavailable ->
                ConfigurationCatalogComparison.Unavailable(remote.status)
        }
}

fun RemoteConfigCatalog.toConfigurationCatalogSnapshot(): ConfigurationCatalogSnapshot =
    ConfigurationCatalogSnapshot(
        sourceRepo = sourceRepo,
        branch = branch,
        generatedAt = generatedAt,
        files = files.map { it.toEntry() },
    )

private fun RemoteConfigFile.toEntry(): ConfigurationCatalogEntry =
    ConfigurationCatalogEntry(
        path = path,
        name = name,
        sha = sha,
        size = size,
        updatedAt = updatedAt,
    )

private fun ManagerConfigurationCatalogDto.toSnapshot(): ConfigurationCatalogSnapshot =
    ConfigurationCatalogSnapshot(
        sourceRepo = sourceRepo,
        branch = branch,
        generatedAt = generatedAt,
        files = files.map { it.toEntry() },
    )

private fun ManagerConfigurationCatalogEntryDto.toEntry(): ConfigurationCatalogEntry =
    ConfigurationCatalogEntry(
        path = path,
        name = name,
        sha = sha,
        size = size,
        updatedAt = updatedAt,
    )

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

private fun ManagerRuntimeAvailability.toStatus(): ConfigurationCatalogReadStatus = when (this) {
    is ManagerRuntimeAvailability.Disconnected -> ConfigurationCatalogReadStatus.DISCONNECTED
    is ManagerRuntimeAvailability.Binding -> ConfigurationCatalogReadStatus.BINDING
    is ManagerRuntimeAvailability.RuntimeMissing -> ConfigurationCatalogReadStatus.RUNTIME_MISSING
    is ManagerRuntimeAvailability.PermissionDenied -> ConfigurationCatalogReadStatus.PERMISSION_DENIED
    is ManagerRuntimeAvailability.TimedOut -> ConfigurationCatalogReadStatus.TIMED_OUT
    is ManagerRuntimeAvailability.Incompatible -> ConfigurationCatalogReadStatus.INCOMPATIBLE
    is ManagerRuntimeAvailability.TemporarilyDisconnected ->
        ConfigurationCatalogReadStatus.TEMPORARILY_DISCONNECTED
    is ManagerRuntimeAvailability.Failed -> ConfigurationCatalogReadStatus.FAILED
    is ManagerRuntimeAvailability.Available -> ConfigurationCatalogReadStatus.FAILED
}
