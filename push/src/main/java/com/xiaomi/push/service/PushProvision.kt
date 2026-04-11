package com.xiaomi.push.service

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.Settings
import com.xiaomi.channel.commonutils.misc.BuildSettings

class PushProvision private constructor(
    context: Context,
) {
    private val appContext = context.applicationContext
    private var provisioned = 0

    companion object {
        @Volatile
        private var instance: PushProvision? = null

        @JvmStatic
        fun getInstance(context: Context): PushProvision {
            return instance ?: synchronized(this) {
                instance ?: PushProvision(context).also { instance = it }
            }
        }
    }

    fun checkProvisioned(): Boolean {
        return BuildSettings.ReleaseChannel.contains("xmsf") ||
            BuildSettings.ReleaseChannel.contains("xiaomi") ||
            BuildSettings.ReleaseChannel.contains("miui")
    }

    fun getProvisioned(): Int {
        if (provisioned != 0) {
            return provisioned
        }
        if (Build.VERSION.SDK_INT >= 17) {
            provisioned = runCatching {
                Settings.Global.getInt(appContext.contentResolver, "device_provisioned", 0)
            }.getOrDefault(0)
            return provisioned
        }
        provisioned = Settings.Secure.getInt(appContext.contentResolver, "device_provisioned", 0)
        return provisioned
    }

    fun getProvisionedUri(): Uri {
        return if (Build.VERSION.SDK_INT >= 17) {
            Settings.Global.getUriFor("device_provisioned")
        } else {
            Settings.Secure.getUriFor("device_provisioned")
        }
    }
}
