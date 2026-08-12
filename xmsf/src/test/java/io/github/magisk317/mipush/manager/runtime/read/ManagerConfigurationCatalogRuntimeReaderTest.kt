package io.github.magisk317.mipush.manager.runtime.read

import io.github.magisk317.mipush.configuration.ConfigCatalogService
import io.github.magisk317.mipush.configuration.ConfigSyncStateStore
import io.github.magisk317.mipush.utils.ConfigRemoteSource
import io.mockk.coEvery
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class ManagerConfigurationCatalogRuntimeReaderTest {
    @Test
    fun `missing cache and failed fetch remains a failed read`() {
        val source = ConfigRemoteSource(repository = "github:example/configs", branch = "main")
        val catalogService = mockk<ConfigCatalogService>()
        val syncStateStore = mockk<ConfigSyncStateStore>()
        coEvery { catalogService.getRemoteSource() } returns source
        coEvery { syncStateStore.getCachedCatalog(source) } returns null
        coEvery { catalogService.fetchCatalog(source) } throws IOException("offline")

        assertThrows(IOException::class.java) {
            runBlocking {
                ManagerConfigurationCatalogRuntimeReader(catalogService, syncStateStore).readCatalog()
            }
        }
        Unit
    }
}
