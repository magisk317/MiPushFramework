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
    fun isNotificationOnRegister(ctx: Context): Boolean {
        return runBlocking { DataStoreManager.notificationOnRegister.first() }
    }

    fun isShowConfigurationListOnLoaded(ctx: Context): Boolean {
        return runBlocking { DataStoreManager.showConfigurationList.first() }
    }

    fun getAccessMode(ctx: Context): Int {
        val mode = runBlocking { DataStoreManager.accessMode.first() }
        return mode.toInt()
    }

    fun isIceboxSupported(ctx: Context): Boolean {
        return runBlocking { DataStoreManager.iceboxSupported.first() }
    }

    fun getConfigurationDirectory(ctx: Context): Uri? {
        val uri = runBlocking { DataStoreManager.configDirectory.first() }
        return if (uri == null) null else Uri.parse(uri)
    }

    fun setConfigurationDirectory(ctx: Context, treeUri: Uri): Boolean {
        runBlocking { DataStoreManager.setConfigDirectory(treeUri.toString()) }
        return true
    }

    fun getXMPPServer(ctx: Context): String? {
        return runBlocking { DataStoreManager.xmppServer.first() }
    }

    fun setXMPPServer(ctx: Context, host: String): Boolean {
        runBlocking { DataStoreManager.setXmppServer(host) }
        return true
    }

    val isDebugMode: Boolean
        get() {
            return runBlocking { DataStoreManager.isDebugMode.first() }
        }

    val isShowAllEvents: Boolean
        get() {
            return runBlocking { DataStoreManager.isShowAllEvents.first() }
        }

    val isStartForegroundService: Boolean
        get() {
            return runBlocking { DataStoreManager.isStartForeground.first() }
        }

    val shouldStartPushAsForegroundService: Boolean
        get() {
            return runBlocking { DataStoreManager.startPushAsForegroundService.first() }
        }

    fun getHazeBlurRadius(ctx: Context): Int {
        return runBlocking { DataStoreManager.hazeBlurRadius.first() }
    }

    fun setHazeBlurRadius(ctx: Context, radius: Int): Boolean {
        runBlocking { DataStoreManager.setHazeBlurRadius(radius) }
        return true
    }

    fun getHazeTintAlpha(ctx: Context): Float {
        return runBlocking { DataStoreManager.hazeTintAlpha.first() }
    }

    fun setHazeTintAlpha(ctx: Context, alpha: Float): Boolean {
        runBlocking { DataStoreManager.setHazeTintAlpha(alpha) }
        return true
    }

    fun loadConfigurations(context: Context) {
        Configurations.getInstance().init(context, this.getConfigurationDirectory(context))
        Global.IconConfigurations().init(context, this.getConfigurationDirectory(context))
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
