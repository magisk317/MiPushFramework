package io.github.magisk317.mipush.manager.runtime.read

import io.github.magisk317.mipush.app.di.AppDependencies
import io.github.magisk317.mipush.configuration.ConfigCatalogService
import io.github.magisk317.mipush.configuration.ConfigSyncStateStore
import io.github.magisk317.mipush.manager.api.ManagerConfigurationCatalogDto
import io.github.magisk317.mipush.manager.api.ManagerConfigurationCatalogEntryDto
import io.github.magisk317.mipush.manager.api.ManagerProtocol
import io.github.magisk317.mipush.utils.RemoteConfigCatalog

/**
 * Returns cached remote configuration catalog metadata only. Local SAF trees and document content
 * remain outside the runtime boundary.
 */
class ManagerConfigurationCatalogRuntimeReader(
    private val catalogService: ConfigCatalogService,
    private val syncStateStore: ConfigSyncStateStore,
) {
    constructor(context: android.content.Context) : this(
        catalogService = AppDependencies.get(context),
        syncStateStore = AppDependencies.get(context),
    )

    suspend fun readCatalog(): ManagerConfigurationCatalogDto {
        val source = catalogService.getRemoteSource()
        val catalog = syncStateStore.getCachedCatalog(source) ?: catalogService.fetchCatalog(source).also {
            syncStateStore.cacheCatalog(source, it)
        }
        return catalog.toWireDto()
    }

    private fun RemoteConfigCatalog.toWireDto(): ManagerConfigurationCatalogDto =
        ManagerConfigurationCatalogDto(
            sourceRepo = sourceRepo.take(ManagerProtocol.MAX_WIRE_STRING_LENGTH),
            branch = branch.take(ManagerProtocol.MAX_WIRE_STRING_LENGTH),
            generatedAt = generatedAt.take(ManagerProtocol.MAX_WIRE_STRING_LENGTH),
            files = files
                .take(ManagerProtocol.MAX_CONFIGURATION_CATALOG_ITEM_COUNT)
                .map { file ->
                    ManagerConfigurationCatalogEntryDto(
                        path = file.path.take(ManagerProtocol.MAX_CONFIGURATION_PATH_LENGTH),
                        name = file.name.take(ManagerProtocol.MAX_CONFIGURATION_NAME_LENGTH),
                        sha = file.sha.take(ManagerProtocol.MAX_CONFIGURATION_SHA_LENGTH),
                        size = file.size.coerceAtLeast(0),
                        updatedAt = file.updatedAt.take(ManagerProtocol.MAX_WIRE_STRING_LENGTH),
                    )
                },
        )
}
