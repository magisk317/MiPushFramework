package com.xiaomi.push.service

import android.content.Context
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.mipush.sdk.Constants
import java.util.Locale

class MIPushAccount(
    @JvmField val account: String,
    @JvmField val token: String,
    @JvmField val security: String,
    @JvmField val appId: String,
    @JvmField val appToken: String,
    @JvmField val packageName: String,
    @JvmField val envType: Int,
) {
    companion object {
        const val PREF_KEY_APP_ID = "app_id"
        const val PREF_KEY_APP_TOKEN = "app_token"
        const val PREF_KEY_DEVICE_ID = "device_id"
        const val PREF_KEY_PACKAGENAME = "package_name"
        const val PREF_KEY_SECURITY = "security"
        const val PREF_KEY_TOKEN = "token"
        const val PREF_KEY_UUID = "uuid"

        @JvmStatic
        fun isAbTestSupported(context: Context): Boolean {
            return PushConstants.PUSH_SERVICE_PACKAGE_NAME == context.packageName && isMIUIAlphaVersion()
        }

        @JvmStatic
        fun isMIUIAlphaVersion(): Boolean {
            return try {
                SystemUtils.loadClass(null, "miui.os.Build").getField("IS_ALPHA_BUILD").getBoolean(null)
            } catch (_: Exception) {
                false
            }
        }

        private fun isMIUIPush(context: Context): Boolean {
            return context.packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME
        }
    }

    fun toClientLoginInfo(
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        context: Context,
        clientEventDispatcher: ClientEventDispatcher,
        abTag: String,
    ): PushClientsManager.ClientLoginInfo {
        clientLoginInfo.pkgName = context.packageName
        clientLoginInfo.userId = account
        clientLoginInfo.security = security
        clientLoginInfo.token = token
        clientLoginInfo.chid = "5"
        clientLoginInfo.authMethod = "XMPUSH-PASS"
        clientLoginInfo.kick = false
        clientLoginInfo.clientExtra = String.format(
            "%1\$s:%2\$s,%3\$s:%4\$s,%5\$s:%6\$s:%7\$s:%8\$s,%9\$s:%10\$s,%11\$s:%12\$s",
            "sdk_ver",
            41,
            PushConstants.KEY_CHANNEL_PUSH_VERSION_NAME,
            PushConstants.PUSH_VERSION_NAME,
            PushConstants.KEY_CHANNEL_PUSH_VERSION_CODE,
            PushConstants.PUSH_VERSION_CODE,
            PushConstants.RUNNING_APP_PACKAGE_NAMES,
            if (isMIUIPush(context)) AppInfoUtils.getRunningAppPkgNames(context) else "",
            PushConstants.KEY_COUNTRY_CODE,
            AppRegionStorage.getInstance(context).getCountryCode(),
            PushConstants.KEY_REGION,
            AppRegionStorage.getInstance(context).getRegion(),
        )
        clientLoginInfo.cloudExtra = String.format(
            "%1\$s:%2\$s,%3\$s:%4\$s,%5\$s:%6\$s,sync:1",
            PushServiceConstants.EXTENSION_ATTRIBUTE_OPENPLATFORM_APPID,
            if (isMIUIPush(context)) MIPushAccountUtils.MIPUSH_MIUI_APPID else appId,
            "locale",
            Locale.getDefault().toString(),
            Constants.EXTRA_KEY_MIID,
            SystemUtils.getMIID(context),
        )
        if (isAbTestSupported(context)) {
            clientLoginInfo.cloudExtra += String.format(",%1\$s:%2\$s", "ab", abTag)
        }
        clientLoginInfo.mClientEventDispatcher = clientEventDispatcher
        return clientLoginInfo
    }

    fun toClientLoginInfo(context: Context): PushClientsManager.ClientLoginInfo {
        return toClientLoginInfo(
            clientLoginInfo = PushClientsManager.ClientLoginInfo(),
            context = context,
            clientEventDispatcher = ClientEventDispatcher(),
            abTag = "d",
        )
    }

    fun toClientLoginInfo(pushAction: IPushServiceAction, context: Context): PushClientsManager.ClientLoginInfo {
        val clientLoginInfo = PushClientsManager.ClientLoginInfo(pushAction)
        return toClientLoginInfo(
            clientLoginInfo = clientLoginInfo,
            context = context,
            clientEventDispatcher = ClientEventDispatcher(), // Default dispatcher if none provided
            abTag = "c",
        )
    }
}
