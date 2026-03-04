@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.magisk317.Global
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.push.service.XMPushService
import com.xiaomi.xmsf.push.utils.Configurations
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import top.trumeet.common.Constants
import top.trumeet.common.utils.Utils
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

    @Deprecated("Use isNotificationOnRegisterAsync()", ReplaceWith("isNotificationOnRegisterAsync()"))
    fun isNotificationOnRegister(ctx: Context): Boolean = runBlocking { isNotificationOnRegisterAsync() }

    suspend fun isShowConfigurationListOnLoadedAsync(): Boolean =
        DataStoreManager.showConfigurationList.first()

    @Deprecated("Use isShowConfigurationListOnLoadedAsync()", ReplaceWith("isShowConfigurationListOnLoadedAsync()"))
    fun isShowConfigurationListOnLoaded(ctx: Context): Boolean = runBlocking { isShowConfigurationListOnLoadedAsync() }

    suspend fun getAccessModeAsync(): Int = DataStoreManager.accessMode.first().toInt()

    @Deprecated("Use getAccessModeAsync()", ReplaceWith("getAccessModeAsync()"))
    fun getAccessMode(ctx: Context): Int = runBlocking { getAccessModeAsync() }

    suspend fun getConfigurationDirectoryAsync(): Uri? {
        val uri = DataStoreManager.configDirectory.first()
        return if (uri.isNullOrBlank()) null else Uri.parse(uri)
    }

    @Deprecated("Use getConfigurationDirectoryAsync()", ReplaceWith("getConfigurationDirectoryAsync()"))
    fun getConfigurationDirectory(ctx: Context): Uri? = runBlocking { getConfigurationDirectoryAsync() }

    suspend fun setConfigurationDirectoryAsync(treeUri: Uri): Boolean {
        DataStoreManager.setConfigDirectory(treeUri.toString())
        return true
    }

    @Deprecated("Use setConfigurationDirectoryAsync()", ReplaceWith("setConfigurationDirectoryAsync(treeUri)"))
    fun setConfigurationDirectory(ctx: Context, treeUri: Uri): Boolean = runBlocking { setConfigurationDirectoryAsync(treeUri) }

    suspend fun getXMPPServerAsync(): String? = DataStoreManager.xmppServer.first()

    @Deprecated("Use getXMPPServerAsync()", ReplaceWith("getXMPPServerAsync()"))
    fun getXMPPServer(ctx: Context): String? = runBlocking { getXMPPServerAsync() }

    suspend fun setXMPPServerAsync(host: String): Boolean {
        DataStoreManager.setXmppServer(host)
        return true
    }

    @Deprecated("Use setXMPPServerAsync()", ReplaceWith("setXMPPServerAsync(host)"))
    fun setXMPPServer(ctx: Context, host: String): Boolean = runBlocking { setXMPPServerAsync(host) }

    suspend fun isDebugModeAsync(): Boolean = DataStoreManager.isDebugMode.first()

    @Deprecated("Use isDebugModeAsync()", ReplaceWith("isDebugModeAsync()"))
    val isDebugMode: Boolean
        get() = runBlocking { isDebugModeAsync() }

    suspend fun isShowAllEventsAsync(): Boolean = DataStoreManager.isShowAllEvents.first()

    @Deprecated("Use isShowAllEventsAsync()", ReplaceWith("isShowAllEventsAsync()"))
    val isShowAllEvents: Boolean
        get() = runBlocking { isShowAllEventsAsync() }

    suspend fun isStartForegroundServiceAsync(): Boolean = DataStoreManager.isStartForeground.first()

    @Deprecated("Use isStartForegroundServiceAsync()", ReplaceWith("isStartForegroundServiceAsync()"))
    val isStartForegroundService: Boolean
        get() = runBlocking { isStartForegroundServiceAsync() }

    suspend fun shouldStartPushAsForegroundServiceAsync(): Boolean =
        DataStoreManager.startPushAsForegroundService.first()

    @Deprecated("Use shouldStartPushAsForegroundServiceAsync()", ReplaceWith("shouldStartPushAsForegroundServiceAsync()"))
    val shouldStartPushAsForegroundService: Boolean
        get() = runBlocking { shouldStartPushAsForegroundServiceAsync() }

    suspend fun getHazeBlurRadiusAsync(): Int = DataStoreManager.hazeBlurRadius.first()

    @Deprecated("Use getHazeBlurRadiusAsync()", ReplaceWith("getHazeBlurRadiusAsync()"))
    fun getHazeBlurRadius(ctx: Context): Int = runBlocking { getHazeBlurRadiusAsync() }

    suspend fun setHazeBlurRadiusAsync(radius: Int): Boolean {
        DataStoreManager.setHazeBlurRadius(radius)
        return true
    }

    @Deprecated("Use setHazeBlurRadiusAsync()", ReplaceWith("setHazeBlurRadiusAsync(radius)"))
    fun setHazeBlurRadius(ctx: Context, radius: Int): Boolean = runBlocking { setHazeBlurRadiusAsync(radius) }

    suspend fun getHazeTintAlphaAsync(): Float = DataStoreManager.hazeTintAlpha.first()

    @Deprecated("Use getHazeTintAlphaAsync()", ReplaceWith("getHazeTintAlphaAsync()"))
    fun getHazeTintAlpha(ctx: Context): Float = runBlocking { getHazeTintAlphaAsync() }

    suspend fun setHazeTintAlphaAsync(alpha: Float): Boolean {
        DataStoreManager.setHazeTintAlpha(alpha)
        return true
    }

    @Deprecated("Use setHazeTintAlphaAsync()", ReplaceWith("setHazeTintAlphaAsync(alpha)"))
    fun setHazeTintAlpha(ctx: Context, alpha: Float): Boolean = runBlocking { setHazeTintAlphaAsync(alpha) }

    fun loadConfigurations(context: Context) {
        val directory = runBlocking { getConfigurationDirectoryAsync() }
        Configurations.getInstance().init(context, directory)
        Global.IconConfigurations().init(context, directory)
        val intent = Intent()
        intent.component = ComponentName(context, XMPushService::class.java)
        intent.action = Constants.CONFIGURATIONS_UPDATE_ACTION
        context.startService(intent)
    }

    companion object {
        @JvmStatic
        fun getSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(
                BuildConfig.APPLICATION_ID + "_preferences",
                Context.MODE_MULTI_PROCESS
            )
        }
    }
}
