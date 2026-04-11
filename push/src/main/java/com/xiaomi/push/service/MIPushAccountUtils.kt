package com.xiaomi.push.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.channel.commonutils.misc.BuildSettings
import com.xiaomi.channel.commonutils.msa.MsaIdManager
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.channel.commonutils.string.XMStringUtils
import com.xiaomi.smack.ConnectionConfiguration
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.util.TreeMap

object MIPushAccountUtils {
    const val MIPUSH_MIUI_APPID = "1000271"
    const val MIPUSH_MIUI_APP_TOKEN = "420100086271"

    private const val PREF_NAME = "mipush_account"
    private const val PREF_KEY_ACCOUNT = "uuid"
    private const val PREF_KEY_TOKEN = "token"
    private const val PREF_KEY_SECURITY = "security"
    private const val PREF_KEY_APP_ID = "app_id"
    private const val PREF_KEY_APP_TOKEN = "app_token"
    private const val PREF_KEY_PACKAGENAME = "package_name"
    private const val PREF_KEY_DEVICE_ID = "device_id"
    private const val PREF_KEY_ENV_TYPE = "env_type"
    private const val PREF_KEY_GAID = "gaid"

    fun interface PushAccountChangeListener {
        fun onChange()
    }

    private var accountChangeListener: PushAccountChangeListener? = null
    private var account: MIPushAccount? = null

    @JvmStatic
    fun clearAccount(context: Context) {
        context.getSharedPreferences(PREF_NAME, 0).edit().clear().commit()
        account = null
        notifyAccountChange()
    }

    @JvmStatic
    fun getAccountURL(context: Context): String {
        return MIPushAccountUtilsRuntime.resolveAccountUrl(
            region = AppRegionStorage.getInstance(context).getRegion(),
            oneBoxBuild = BuildSettings.IsOneBoxBuild(),
            oneBoxHost = ConnectionConfiguration.XMPP_SERVER_HOST_ONEBOX,
            sandBoxBuild = BuildSettings.IsSandBoxBuild(),
        )
    }

    @JvmStatic
    fun getMIPushAccount(context: Context): MIPushAccount? {
        synchronized(MIPushAccountUtils::class.java) {
            account?.let { return it }
            val sharedPreferences = context.getSharedPreferences(PREF_NAME, 0)
            val accountId = sharedPreferences.getString(PREF_KEY_ACCOUNT, null)
            val token = sharedPreferences.getString(PREF_KEY_TOKEN, null)
            val security = sharedPreferences.getString(PREF_KEY_SECURITY, null)
            val appId = sharedPreferences.getString(PREF_KEY_APP_ID, null)
            val appToken = sharedPreferences.getString(PREF_KEY_APP_TOKEN, null)
            val packageName = sharedPreferences.getString(PREF_KEY_PACKAGENAME, null)
            val deviceId = sharedPreferences.getString(PREF_KEY_DEVICE_ID, null)
            val envType = sharedPreferences.getInt(PREF_KEY_ENV_TYPE, 1)

            val normalizedDeviceId = when {
                deviceId.isNullOrEmpty() -> deviceId
                DeviceInfo.startsWithDevPrefix(deviceId) -> {
                    DeviceInfo.getSimpleDeviceId(context).also {
                        sharedPreferences.edit().putString(PREF_KEY_DEVICE_ID, it).commit()
                    }
                }
                else -> deviceId
            }

            if (accountId.isNullOrEmpty() || token.isNullOrEmpty() || security.isNullOrEmpty()) {
                return null
            }
            if (appId == null || appToken == null || packageName == null) {
                return null
            }
            val currentDeviceId = DeviceInfo.getSimpleDeviceId(context)
            if (context.packageName != PushConstants.PUSH_SERVICE_PACKAGE_NAME &&
                !currentDeviceId.isNullOrEmpty() &&
                !normalizedDeviceId.isNullOrEmpty() &&
                normalizedDeviceId != currentDeviceId
            ) {
                MyLog.w("read_phone_state permission changes.")
            }

            return MIPushAccount(
                account = accountId,
                token = token,
                security = security,
                appId = appId,
                appToken = appToken,
                packageName = packageName,
                envType = envType,
            ).also { account = it }
        }
    }

    @JvmStatic
    fun notifyAccountChange() {
        accountChangeListener?.onChange()
    }

    @JvmStatic
    fun persist(context: Context, account: MIPushAccount) {
        context.getSharedPreferences(PREF_NAME, 0).edit().apply {
            putString(PREF_KEY_ACCOUNT, account.account)
            putString(PREF_KEY_SECURITY, account.security)
            putString(PREF_KEY_TOKEN, account.token)
            putString(PREF_KEY_APP_ID, account.appId)
            putString(PREF_KEY_PACKAGENAME, account.packageName)
            putString(PREF_KEY_APP_TOKEN, account.appToken)
            putString(PREF_KEY_DEVICE_ID, DeviceInfo.getSimpleDeviceId(context))
            putInt(PREF_KEY_ENV_TYPE, account.envType)
            commit()
        }
        notifyAccountChange()
    }

    @JvmStatic
    @Throws(JSONException::class, IOException::class)
    fun register(context: Context, packageName: String?, appId: String?, appToken: String?): MIPushAccount? {
        synchronized(MIPushAccountUtils::class.java) {
            val params = TreeMap<String, String>()
            val deviceId = DeviceInfo.getDeviceId(context, false)
            MyLog.w("account register:$deviceId mim:${MsaIdManager.getInstance(context).toShortString()}")
            params["devid"] = deviceId

            var accountResource: String? = null
            account?.takeIf { !it.account.isNullOrEmpty() }?.let { existing ->
                params["uuid"] = existing.account
                val slashIndex = existing.account.lastIndexOf("/")
                if (slashIndex != -1) {
                    accountResource = existing.account.substring(slashIndex + 1)
                }
            }

            MsaIdManager.getInstance(context).fillData(params)

            DeviceInfo.getVirtDevId(context)?.takeIf { it.isNotEmpty() }?.let {
                params["vdevid"] = it
            }
            DeviceInfo.getGaid(context)?.takeIf { it.isNotEmpty() }?.let {
                params[PREF_KEY_GAID] = it
            }

            val resolvedAppId = if (isMIUIPush(context)) MIPUSH_MIUI_APPID else appId ?: return null
            val resolvedAppToken = if (isMIUIPush(context)) MIPUSH_MIUI_APP_TOKEN else appToken ?: return null
            val resolvedPackageName = if (isMIUIPush(context)) PushConstants.PUSH_SERVICE_PACKAGE_NAME else packageName ?: return null

            params["appid"] = resolvedAppId
            params["apptoken"] = resolvedAppToken
            params["appversion"] = AppInfoUtils.getVersionCode(context, resolvedPackageName).toString()
            params["sdkversion"] = "30709"
            params["packagename"] = resolvedPackageName
            params["model"] = Build.MODEL
            params["board"] = Build.BOARD

            if (!MIUIUtils.isGlobalRegion()) {
                var imeiMd5 = ""
                DeviceInfo.blockingGetIMEI(context)?.takeIf { it.isNotEmpty() }?.let {
                    imeiMd5 = XMStringUtils.getMd5Digest(it)
                }
                val subImeiMd5 = DeviceInfo.blockingGetSubIMEISMd5(context)
                if (imeiMd5.isNotEmpty() && !subImeiMd5.isNullOrEmpty()) {
                    imeiMd5 = "$imeiMd5,$subImeiMd5"
                }
                if (imeiMd5.isNotEmpty()) {
                    params["imei_md5"] = imeiMd5
                }
            }

            params["os"] = "${Build.VERSION.RELEASE}-${Build.VERSION.INCREMENTAL}"
            val spaceId = DeviceInfo.getSpaceId()
            if (spaceId >= 0) {
                params["space_id"] = spaceId.toString()
            }
            params["brand"] = Build.BRAND.toString()
            params["ram"] = DeviceInfo.getRamSize()
            params["rom"] = DeviceInfo.getRomSize()

            val responseString = Network.doHttpPost(context, getAccountURL(context), params)?.responseString.orEmpty()
            if (responseString.isEmpty()) {
                return null
            }

            val responseJson = JSONObject(responseString)
            if (responseJson.getInt("code") != 0) {
                MIPushClientManager.notifyRegisterError(
                    context,
                    responseJson.getInt("code"),
                    responseJson.optString("description"),
                )
                MyLog.w(responseString)
                return null
            }

            val data = responseJson.getJSONObject("data")
            val security = data.getString("ssecurity")
            val token = data.getString("token")
            val userId = data.getString("userId")
            val resolvedResource = accountResource ?: "an${XMStringUtils.generateRandomString(6)}"
            val createdAccount = MIPushAccount(
                account = "$userId@xiaomi.com/$resolvedResource",
                token = token,
                security = security,
                appId = resolvedAppId,
                appToken = resolvedAppToken,
                packageName = resolvedPackageName,
                envType = BuildSettings.getEnvType(),
            )
            persist(context, createdAccount)
            DeviceInfo.updateVirtDevId(context, data.optString("vdevid"))
            account = createdAccount
            return createdAccount
        }
    }

    @JvmStatic
    fun setAccountChangeListener(listener: PushAccountChangeListener?) {
        accountChangeListener = listener
    }

    private fun isMIUIPush(context: Context): Boolean {
        return context.packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME
    }
}
