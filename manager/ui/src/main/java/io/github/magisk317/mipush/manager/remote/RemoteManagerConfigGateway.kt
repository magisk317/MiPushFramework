package io.github.magisk317.mipush.manager.remote

import android.content.Context
import android.net.Uri
import io.github.magisk317.mipush.manager.application.ManagerConfigGateway
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.manager.preferences.RuntimePreferenceGateway
import kotlinx.coroutines.flow.first

class RemoteManagerConfigGateway(
    private val preferenceRepository: PreferenceRepository,
    private val configSyncGateway: io.github.magisk317.mipush.manager.configuration.sync.LocalManagerConfigSyncGateway,
    private val runtimePreferenceGateway: RuntimePreferenceGateway,
) : ManagerConfigGateway {
    override suspend fun getXmppServer(): String? = runtimePreferenceGateway.getXmppServer()

    override suspend fun setXmppServer(host: String): Boolean {
        return runtimePreferenceGateway.setXmppServer(host)
    }

    override suspend fun getConfigurationDirectory(): Uri? =
        preferenceRepository.configDirectory.first()?.let(Uri::parse)

    override suspend fun setConfigurationDirectory(uri: Uri): Boolean {
        preferenceRepository.setConfigDirectory(uri.toString())
        return true
    }

    override suspend fun loadConfigurations(context: Context) {
        val tree = preferenceRepository.configDirectory.first()?.let(Uri::parse)
        configSyncGateway.activateAllLocalConfigs(tree)
    }
}
