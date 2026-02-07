@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf.utils

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import com.nihility.Global
import com.xiaomi.xmsf.BuildConfig
import com.xiaomi.xmsf.push.service.XMPushService
import com.xiaomi.xmsf.push.utils.Configurations
import top.trumeet.common.Constants
import top.trumeet.common.utils.Utils

/**
 * Push 配置
 */
class ConfigCenter {
    fun isNotificationOnRegister(ctx: Context): Boolean {
        return getSharedPreferences(ctx).getBoolean("NotificationOnRegister", false)
    }

    fun isShowConfigurationListOnLoaded(ctx: Context): Boolean {
        return getSharedPreferences(ctx).getBoolean("ShowConfigurationListOnLoaded", false)
    }

    fun getAccessMode(ctx: Context): Int {
        val mode = getSharedPreferences(ctx).getString("AccessMode", "0") ?: "0"
        return mode.toInt()
    }

    fun isIceboxSupported(ctx: Context): Boolean {
        return getSharedPreferences(ctx).getBoolean("IceboxSupported", false)
    }

    fun getConfigurationDirectory(ctx: Context): Uri? {
        val uri = getSharedPreferences(ctx).getString("ConfigurationDirectory", null)
        return if (uri == null) null else Uri.parse(uri)
    }

    fun setConfigurationDirectory(ctx: Context, treeUri: Uri): Boolean {
        return getSharedPreferences(ctx).edit().putString("ConfigurationDirectory", treeUri.toString()).commit()
    }

    fun getXMPPServer(ctx: Context): String? {
        return getSharedPreferences(ctx).getString("XMPP_server", null)
    }

    fun setXMPPServer(ctx: Context, host: String): Boolean {
        return getSharedPreferences(ctx).edit().putString("XMPP_server", host).commit()
    }

    val isDebugMode: Boolean
        get() {
            val app = Utils.getApplication() ?: return false
            return getSharedPreferences(app).getBoolean("DebugMode", false)
        }

    val isShowAllEvents: Boolean
        get() {
            val app = Utils.getApplication() ?: return false
            return getSharedPreferences(app).getBoolean("ShowAllEvents", false)
        }

    val isStartForegroundService: Boolean
        get() {
            val app = Utils.getApplication() ?: return false
            return getSharedPreferences(app).getBoolean("StartForegroundService", false)
        }

    fun loadConfigurations(context: Context) {
        Configurations.getInstance().init(context, Global.ConfigCenter().getConfigurationDirectory(context))
        Global.IconConfigurations().init(context, Global.ConfigCenter().getConfigurationDirectory(context))
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
