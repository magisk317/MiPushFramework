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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.github.magisk317.mipush.common.Constants

/**
 * Push 配置
 */
class ConfigCenter constructor(
    private val preferenceRepository: PreferenceRepository
) {
    constructor() : this(PreferenceRepository())

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

    suspend fun getHazeBlurRadiusAsync(): Int = preferenceRepository.hazeBlurRadius.first()

    suspend fun setHazeBlurRadiusAsync(radius: Int): Boolean {
        preferenceRepository.setHazeBlurRadius(radius)
        return true
    }

    suspend fun getHazeTintAlphaAsync(): Float = preferenceRepository.hazeTintAlpha.first()

    suspend fun setHazeTintAlphaAsync(alpha: Float): Boolean {
        preferenceRepository.setHazeTintAlpha(alpha)
        return true
    }

    fun loadConfigurations(context: Context) {
        // Use a cached directory value to avoid blocking the calling thread.
        // The configuration directory rarely changes and is set explicitly by the user.
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
            loadConfigurationsNow(context)
        }
    }

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
            val intent = PushRuntimeComponents.newLegacyMainServiceIntent(
                appContext,
                Constants.CONFIGURATIONS_UPDATE_ACTION
            )
            PushServiceStarter.start(appContext, intent)
        }
        return configLoaded && iconLoaded
    }
}
