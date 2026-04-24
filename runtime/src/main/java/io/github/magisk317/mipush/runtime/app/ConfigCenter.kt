package io.github.magisk317.mipush.runtime.app

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.xiaomi.push.service.XMPushService
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.runtime.core.data.PreferenceRepository
import io.github.magisk317.mipush.common.configurations.IConfigProvider
import io.github.magisk317.mipush.common.configurations.Configurations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import io.github.magisk317.mipush.common.Constants

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Push 配置
 */
@Singleton
class ConfigCenter @Inject constructor(
    private val preferenceRepository: PreferenceRepository
) : IConfigProvider {
    constructor() : this(PreferenceRepository())

    init {
        try {
            io.github.magisk317.mipush.common.utils.Singleton.reset(this)
        } catch (t: Throwable) {
            io.github.aakira.napier.Napier.w("Singleton.reset failed for ConfigCenter", t, tag = "ConfigCenter")
        }
    }
    suspend fun isNotificationOnRegisterAsync(): Boolean =
        preferenceRepository.notificationOnRegister.first()

    override suspend fun isShowConfigurationListOnLoadedAsync(): Boolean =
        preferenceRepository.showConfigurationList.first()

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
            val directory = getConfigurationDirectoryAsync()
            Configurations.getInstance().init(context, directory)
            Global.iconConfigurations().init(context, directory)
            val intent = Intent()
            intent.component = ComponentName(context, XMPushService::class.java)
            intent.action = Constants.CONFIGURATIONS_UPDATE_ACTION
            context.startService(intent)
        }
    }
}
