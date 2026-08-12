package io.github.magisk317.mipush.app

import android.content.Context
import android.net.Uri
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.data.PreferenceRepository
import io.github.magisk317.mipush.control.PushControllerUtils
import io.github.magisk317.mipush.runtime.PushRuntimeComponents
import io.github.magisk317.mipush.service.PushServiceStarter
import io.github.magisk317.mipush.utils.Configurations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.Constants

/**
 * Push 配置
 */
class ConfigCenter constructor(
    private val preferenceRepository: PreferenceRepository
) {
    suspend fun getConfigurationDirectoryAsync(): Uri? {
        val uri = preferenceRepository.configDirectory.first()
        return if (uri.isNullOrBlank()) null else Uri.parse(uri)
    }

    suspend fun setConfigurationDirectoryAsync(treeUri: Uri): Boolean {
        preferenceRepository.setConfigDirectory(treeUri.toString())
        return true
    }

    suspend fun getXMPPServerAsync(): String? = preferenceRepository.xmppServer.first()

    suspend fun setXMPPServerAsync(host: String): Boolean {
        preferenceRepository.setXmppServer(host)
        return true
    }

    suspend fun isDebugModeAsync(): Boolean = preferenceRepository.isDebugMode.first()

    suspend fun isShowAllEventsAsync(): Boolean = preferenceRepository.isShowAllEvents.first()

    suspend fun isStartForegroundServiceAsync(): Boolean = preferenceRepository.isStartForeground.first()

    suspend fun shouldStartPushAsForegroundServiceAsync(): Boolean =
        preferenceRepository.startPushAsForegroundService.first()

    suspend fun loadConfigurationsNow(context: Context): Boolean =
        withContext(kotlinx.coroutines.Dispatchers.IO) {
            val appContext = context.applicationContext
            val directory = getConfigurationDirectoryAsync()
            loadConfigurationsFromDirectory(appContext, directory)
        }

    internal fun loadConfigurationsFromDirectory(context: Context, directory: Uri?): Boolean {
        val appContext = context.applicationContext
        val configLoaded = Configurations.getInstance().init(appContext, directory)
        val iconLoaded = Global.iconConfigurations().init(appContext, directory)
        if (!PushControllerUtils.isAppMainProc(appContext)) {
            val intent = PushRuntimeComponents.newCoreServiceIntent(
                appContext,
                Constants.CONFIGURATIONS_UPDATE_ACTION
            )
            PushServiceStarter.start(appContext, intent)
        }
        return configLoaded && iconLoaded
    }
}
