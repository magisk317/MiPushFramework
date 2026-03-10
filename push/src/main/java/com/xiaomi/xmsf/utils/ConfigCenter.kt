package com.xiaomi.xmsf.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.magisk317.Global
import com.xiaomi.xmsf.push.service.XMPushService
import com.xiaomi.xmsf.push.utils.Configurations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import top.trumeet.common.Constants
import com.magisk317.data.DataStoreManager

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Push 配置
 */
@Singleton
class ConfigCenter @Inject constructor() {
    init {
        try {
            com.magisk317.utils.Singleton.reset(this)
        } catch (_: Throwable) {}
    }
    suspend fun isNotificationOnRegisterAsync(): Boolean =
        DataStoreManager.notificationOnRegister.first()

    suspend fun isShowConfigurationListOnLoadedAsync(): Boolean =
        DataStoreManager.showConfigurationList.first()

    suspend fun getAccessModeAsync(): Int = DataStoreManager.accessMode.first().toInt()

    suspend fun getConfigurationDirectoryAsync(): Uri? {
        val uri = DataStoreManager.configDirectory.first()
        return if (uri.isNullOrBlank()) null else Uri.parse(uri)
    }

    suspend fun setConfigurationDirectoryAsync(treeUri: Uri): Boolean {
        DataStoreManager.setConfigDirectory(treeUri.toString())
        return true
    }

    suspend fun getXMPPServerAsync(): String? = DataStoreManager.xmppServer.first()

    suspend fun setXMPPServerAsync(host: String): Boolean {
        DataStoreManager.setXmppServer(host)
        return true
    }

    suspend fun isDebugModeAsync(): Boolean = DataStoreManager.isDebugMode.first()

    suspend fun isShowAllEventsAsync(): Boolean = DataStoreManager.isShowAllEvents.first()

    suspend fun isStartForegroundServiceAsync(): Boolean = DataStoreManager.isStartForeground.first()

    suspend fun shouldStartPushAsForegroundServiceAsync(): Boolean =
        DataStoreManager.startPushAsForegroundService.first()

    suspend fun getHazeBlurRadiusAsync(): Int = DataStoreManager.hazeBlurRadius.first()

    suspend fun setHazeBlurRadiusAsync(radius: Int): Boolean {
        DataStoreManager.setHazeBlurRadius(radius)
        return true
    }

    suspend fun getHazeTintAlphaAsync(): Float = DataStoreManager.hazeTintAlpha.first()

    suspend fun setHazeTintAlphaAsync(alpha: Float): Boolean {
        DataStoreManager.setHazeTintAlpha(alpha)
        return true
    }

    fun loadConfigurations(context: Context) {
        val directory = runBlocking { getConfigurationDirectoryAsync() }
        Configurations.getInstance().init(context, directory)
        Global.IconConfigurations().init(context, directory)
        val intent = Intent()
        intent.component = ComponentName(context, XMPushService::class.java)
        intent.action = Constants.CONFIGURATIONS_UPDATE_ACTION
        context.startService(intent)
    }
}
