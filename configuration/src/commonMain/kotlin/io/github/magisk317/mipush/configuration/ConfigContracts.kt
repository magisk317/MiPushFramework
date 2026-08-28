package io.github.magisk317.mipush.configuration

import io.github.magisk317.mipush.core.configuration.ConfigListItem
import io.github.magisk317.mipush.core.configuration.RemoteConfigCatalog

data class ConfigListSnapshot(
    val catalog: RemoteConfigCatalog? = null,
    val items: List<ConfigListItem> = emptyList(),
    val remoteError: String? = null,
)

fun interface ConfigSyncObserver {
    fun onRecordsUpserted(count: Int)

    data object None : ConfigSyncObserver {
        override fun onRecordsUpserted(count: Int) = Unit
    }
}
