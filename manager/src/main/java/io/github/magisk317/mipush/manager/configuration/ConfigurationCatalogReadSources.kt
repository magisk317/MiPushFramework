package io.github.magisk317.mipush.manager.configuration

import io.github.magisk317.mipush.manager.api.ManagerConfigurationCatalogDto
import io.github.magisk317.mipush.manager.api.ManagerConfigurationCatalogEntryDto
import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability
import io.github.magisk317.mipush.manager.client.ManagerRuntimeClient
import io.github.magisk317.mipush.manager.client.ManagerRuntimeResult
import io.github.magisk317.mipush.utils.RemoteConfigCatalog
import io.github.magisk317.mipush.utils.RemoteConfigFile
import io.github.magisk317.mipush.manager.remote.PageRemoteCallAdapter
import io.github.magisk317.mipush.manager.remote.PageRemoteCallPolicy
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

class RemoteConfigurationCatalogSource internal constructor(
    private val loader: suspend () -> ManagerRuntimeResult<ManagerConfigurationCatalogDto>,
    private val pageCallAdapter: PageRemoteCallAdapter? = null,
) {
    constructor(client: ManagerRuntimeClient, pageCallAdapter: PageRemoteCallAdapter? = null) : this(
        loader = client::getConfigurationCatalog,
        pageCallAdapter = pageCallAdapter,
    )

    suspend fun load(): ConfigurationCatalogReadResult<ConfigurationCatalogSnapshot> = try {
        when (val result = loadCatalog()) {
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

    private suspend fun loadCatalog(): ManagerRuntimeResult<ManagerConfigurationCatalogDto> {
        val adapter = pageCallAdapter ?: return loader()
        return when (val scheduled = adapter.call(
            operation = "configuration_catalog",
            budget = PageRemoteCallPolicy.visiblePage,
        ) { loader() }) {
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Success -> scheduled.value
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Unavailable ->
                ManagerRuntimeResult.Unavailable(scheduled.availability)
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Busy,
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Timeout,
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Cancelled,
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.Stale,
            is io.github.magisk317.mipush.manager.client.ManagerRuntimeCallResult.ValidationFailed,
            -> ManagerRuntimeResult.Failed("stale_or_cancelled")
        }
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
