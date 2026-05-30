package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushVersionInfo
import org.json.JSONObject
import java.util.HashMap

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/AppInfoHolder.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
class AppInfoHolder private constructor(private val mContext: Context) {
    @JvmField
    var appRegRequestId: String? = null

    private var mHybridAppInfoCache: MutableMap<String, ClientInfoData> = HashMap()
    private var mInfoData: ClientInfoData = ClientInfoData(mContext)

    class ClientInfoData(private val mContext: Context) {
        @JvmField var appID: String? = null
        @JvmField var appRegion: String? = null
        @JvmField var appToken: String? = null
        @JvmField var deviceId: String? = null
        @JvmField var regID: String? = null
        @JvmField var regResource: String? = null
        @JvmField var regSecret: String? = null
        @JvmField var versionName: String? = null
        @JvmField var isValid: Boolean = true
        @JvmField var isPaused: Boolean = false
        @JvmField var envType: Int = 1

        private fun getVersionName(): String {
            return PushVersionInfo.reportedAppVersionName(
                mContext.packageName,
                AppInfoUtils.getVersionName(mContext, mContext.packageName)
            )
        }

        fun clear() {
            getSharedPreferences(mContext).edit().clear().commit()
            appID = null
            appToken = null
            regID = null
            regSecret = null
            deviceId = null
            versionName = null
            isValid = false
            isPaused = false
            appRegion = null
            envType = 1
        }

        fun invalidate() {
            isValid = false
            getSharedPreferences(mContext).edit().putBoolean(PREF_KEY_VALID, isValid).commit()
        }

        fun isVaild(): Boolean {
            return isVaild(appID, appToken)
        }

        fun isVaild(str: String?, str2: String?): Boolean {
            return TextUtils.equals(appID, str) &&
                TextUtils.equals(appToken, str2) &&
                !TextUtils.isEmpty(regID) &&
                !TextUtils.isEmpty(regSecret) &&
                (
                    TextUtils.equals(deviceId, DeviceInfo.getInstanceId(mContext)) ||
                        TextUtils.equals(deviceId, DeviceInfo.getSimpleDeviceId(mContext))
                    )
        }

        fun setEnvType(i: Int) {
            envType = i
        }

        fun setHybridIdAndTokenAndPackage(str: String?, str2: String?, str3: String?) {
            appID = str
            appToken = str2
            regResource = str3
        }

        fun setHybridRegIdAndSecret(str: String?, str2: String?) {
            regID = str
            regSecret = str2
            deviceId = DeviceInfo.getInstanceId(mContext)
            versionName = getVersionName()
            isValid = true
        }

        fun setIdAndToken(str: String?, str2: String?, str3: String?) {
            appID = str
            appToken = str2
            regResource = str3
            val editor = getSharedPreferences(mContext).edit()
            editor.putString(PREF_KEY_APP_ID, appID)
            editor.putString(PREF_KEY_APP_TOKEN, str2)
            editor.putString(PREF_KEY_REG_RESOURCE, str3)
            editor.commit()
        }

        fun setPaused(z: Boolean) {
            isPaused = z
        }

        fun setRegIdAndSecret(str: String?, str2: String?, str3: String?) {
            regID = str
            regSecret = str2
            deviceId = DeviceInfo.getInstanceId(mContext)
            versionName = getVersionName()
            isValid = true
            appRegion = str3
            val editor = getSharedPreferences(mContext).edit()
            editor.putString(PREF_KEY_REG_ID, str)
            editor.putString(PREF_KEY_REG_SECRET, str2)
            editor.putString(PREF_KEY_DEVICE_ID, deviceId)
            editor.putString(PREF_KEY_VERSION_NAME, getVersionName())
            editor.putBoolean(PREF_KEY_VALID, true)
            editor.putString(PREF_KEY_APP_REGION, str3)
            editor.commit()
        }

        companion object {
            @JvmStatic
            fun parseClientInfoData(context: Context, str: String?): ClientInfoData? {
                return try {
                    val jsonObject = JSONObject(str!!)
                    ClientInfoData(context).apply {
                        appID = jsonObject.getString(PREF_KEY_APP_ID)
                        appToken = jsonObject.getString(PREF_KEY_APP_TOKEN)
                        regID = jsonObject.getString(PREF_KEY_REG_ID)
                        regSecret = jsonObject.getString(PREF_KEY_REG_SECRET)
                        deviceId = jsonObject.getString(PREF_KEY_DEVICE_ID)
                        versionName = jsonObject.getString(PREF_KEY_VERSION_NAME)
                        isValid = jsonObject.getBoolean(PREF_KEY_VALID)
                        isPaused = jsonObject.getBoolean(PREF_KEY_PAUSED)
                        envType = jsonObject.getInt(PREF_KEY_ENV_TYPE)
                        regResource = jsonObject.getString(PREF_KEY_REG_RESOURCE)
                    }
                } catch (throwable: Throwable) {
                    MyLog.e(throwable)
                    null
                }
            }

            @JvmStatic
            fun toString(clientInfoData: ClientInfoData): String? {
                return try {
                    JSONObject().apply {
                        put(PREF_KEY_APP_ID, clientInfoData.appID)
                        put(PREF_KEY_APP_TOKEN, clientInfoData.appToken)
                        put(PREF_KEY_REG_ID, clientInfoData.regID)
                        put(PREF_KEY_REG_SECRET, clientInfoData.regSecret)
                        put(PREF_KEY_DEVICE_ID, clientInfoData.deviceId)
                        put(PREF_KEY_VERSION_NAME, clientInfoData.versionName)
                        put(PREF_KEY_VALID, clientInfoData.isValid)
                        put(PREF_KEY_PAUSED, clientInfoData.isPaused)
                        put(PREF_KEY_ENV_TYPE, clientInfoData.envType)
                        put(PREF_KEY_REG_RESOURCE, clientInfoData.regResource)
                    }.toString()
                } catch (throwable: Throwable) {
                    MyLog.e(throwable)
                    null
                }
            }
        }
    }

    val appID: String?
        get() = mInfoData.appID

    val appId: String?
        get() = appID

    val appRegion: String?
        get() = mInfoData.appRegion

    val appToken: String?
        get() = mInfoData.appToken

    val envType: Int
        get() = mInfoData.envType

    val regID: String?
        get() = mInfoData.regID

    val regResource: String?
        get() = mInfoData.regResource

    val regSecret: String?
        get() = mInfoData.regSecret

    val isPaused: Boolean
        get() = mInfoData.isPaused

    private fun init() {
        mInfoData = ClientInfoData(mContext)
        mHybridAppInfoCache = HashMap()
        val sharedPreferences = getSharedPreferences(mContext)
        mInfoData.appID = sharedPreferences.getString(PREF_KEY_APP_ID, null)
        mInfoData.appToken = sharedPreferences.getString(PREF_KEY_APP_TOKEN, null)
        mInfoData.regID = sharedPreferences.getString(PREF_KEY_REG_ID, null)
        mInfoData.regSecret = sharedPreferences.getString(PREF_KEY_REG_SECRET, null)
        mInfoData.deviceId = sharedPreferences.getString(PREF_KEY_DEVICE_ID, null)
        if (!TextUtils.isEmpty(mInfoData.deviceId) && DeviceInfo.startsWithDevPrefix(mInfoData.deviceId)) {
            mInfoData.deviceId = DeviceInfo.getInstanceId(mContext)
            sharedPreferences.edit().putString(PREF_KEY_DEVICE_ID, mInfoData.deviceId).commit()
        }
        mInfoData.versionName = sharedPreferences.getString(PREF_KEY_VERSION_NAME, null)
        mInfoData.isValid = sharedPreferences.getBoolean(PREF_KEY_VALID, true)
        mInfoData.isPaused = sharedPreferences.getBoolean(PREF_KEY_PAUSED, false)
        mInfoData.envType = sharedPreferences.getInt(PREF_KEY_ENV_TYPE, 1)
        mInfoData.regResource = sharedPreferences.getString(PREF_KEY_REG_RESOURCE, null)
        mInfoData.appRegion = sharedPreferences.getString(PREF_KEY_APP_REGION, null)
    }

    fun appRegistered(): Boolean {
        return mInfoData.isVaild()
    }

    fun appRegistered(str: String?, str2: String?): Boolean {
        return mInfoData.isVaild(str, str2)
    }

    fun registrationStateSummary(expectedAppId: String? = mInfoData.appID, expectedAppToken: String? = mInfoData.appToken): String {
        val instanceId = runCatching { DeviceInfo.getInstanceId(mContext) }.getOrNull()
        val simpleDeviceId = runCatching { DeviceInfo.getSimpleDeviceId(mContext) }.getOrNull()
        val deviceIdPresent = !TextUtils.isEmpty(mInfoData.deviceId)
        return "valid=${mInfoData.isValid}" +
            " appIdPresent=${!TextUtils.isEmpty(mInfoData.appID)}" +
            " appTokenPresent=${!TextUtils.isEmpty(mInfoData.appToken)}" +
            " appIdMatch=${TextUtils.equals(mInfoData.appID, expectedAppId)}" +
            " appTokenMatch=${TextUtils.equals(mInfoData.appToken, expectedAppToken)}" +
            " regIdPresent=${!TextUtils.isEmpty(mInfoData.regID)}" +
            " regSecretPresent=${!TextUtils.isEmpty(mInfoData.regSecret)}" +
            " deviceIdPresent=$deviceIdPresent" +
            " instanceDeviceMatch=${deviceIdPresent && TextUtils.equals(mInfoData.deviceId, instanceId)}" +
            " simpleDeviceMatch=${deviceIdPresent && TextUtils.equals(mInfoData.deviceId, simpleDeviceId)}" +
            " envType=${mInfoData.envType}" +
            " regionPresent=${!TextUtils.isEmpty(mInfoData.appRegion)}" +
            " requestIdPresent=${!TextUtils.isEmpty(appRegRequestId)}"
    }

    fun checkAppInfo(): Boolean {
        if (mInfoData.isVaild()) {
            return true
        }
        MyLog.w("Don't send message before initialization succeeded!")
        return false
    }

    fun checkVersionNameChanged(): Boolean {
        return !TextUtils.equals(
            PushVersionInfo.reportedAppVersionName(mContext.packageName, AppInfoUtils.getVersionName(mContext, mContext.packageName)),
            mInfoData.versionName
        )
    }

    fun clear() {
        mInfoData.clear()
    }

    fun delHybridAppInfo(str: String) {
        mHybridAppInfoCache.remove(str)
        getSharedPreferences(mContext).edit().remove(PREF_KEY_HYBRID_APP_INFO_PREFIX + str).commit()
    }

    fun getHybridAppInfo(str: String): ClientInfoData? {
        if (mHybridAppInfoCache.containsKey(str)) {
            return mHybridAppInfoCache[str]
        }
        val key = PREF_KEY_HYBRID_APP_INFO_PREFIX + str
        val sharedPreferences = getSharedPreferences(mContext)
        if (!sharedPreferences.contains(key)) {
            return null
        }
        val clientInfoData = ClientInfoData.parseClientInfoData(mContext, sharedPreferences.getString(key, "")) ?: return null
        mHybridAppInfoCache[key] = clientInfoData
        return clientInfoData
    }

    fun invalidate() {
        mInfoData.invalidate()
    }

    fun invalidated(): Boolean {
        return !mInfoData.isValid
    }

    fun isHybridAppRegistered(str: String?, str2: String?, str3: String): Boolean {
        val hybridAppInfo = getHybridAppInfo(str3)
        return hybridAppInfo != null &&
            TextUtils.equals(str, hybridAppInfo.appID) &&
            TextUtils.equals(str2, hybridAppInfo.appToken)
    }

    fun putAppIDAndToken(str: String?, str2: String?, str3: String?) {
        mInfoData.setIdAndToken(str, str2, str3)
    }

    fun putRegIDAndSecret(str: String?, str2: String?, str3: String?) {
        mInfoData.setRegIdAndSecret(str, str2, str3)
    }

    fun saveHybridAppInfo(str: String, clientInfoData: ClientInfoData) {
        mHybridAppInfoCache[str] = clientInfoData
        getSharedPreferences(mContext).edit()
            .putString(PREF_KEY_HYBRID_APP_INFO_PREFIX + str, ClientInfoData.toString(clientInfoData))
            .commit()
    }

    fun setEnvType(i: Int) {
        mInfoData.setEnvType(i)
        getSharedPreferences(mContext).edit().putInt(PREF_KEY_ENV_TYPE, i).commit()
    }

    fun setPaused(z: Boolean) {
        mInfoData.setPaused(z)
        getSharedPreferences(mContext).edit().putBoolean(PREF_KEY_PAUSED, z).commit()
    }

    fun updateVersionName(str: String?) {
        getSharedPreferences(mContext).edit().putString(PREF_KEY_VERSION_NAME, str).commit()
        mInfoData.versionName = str
    }

    companion object {
        private const val PREF_KEY_APP_ID = "appId"
        private const val PREF_KEY_APP_REGION = "appRegion"
        private const val PREF_KEY_APP_TOKEN = "appToken"
        private const val PREF_KEY_DEVICE_ID = "devId"
        private const val PREF_KEY_ENV_TYPE = "envType"
        private const val PREF_KEY_HYBRID_APP_INFO_PREFIX = "hybrid_app_info_"
        private const val PREF_KEY_PAUSED = "paused"
        private const val PREF_KEY_REG_ID = "regId"
        private const val PREF_KEY_REG_RESOURCE = "regResource"
        private const val PREF_KEY_REG_SECRET = "regSec"
        private const val PREF_KEY_VALID = "valid"
        private const val PREF_KEY_VERSION_NAME = "vName"

        @Volatile
        private var sInstance: AppInfoHolder? = null

        @JvmStatic
        fun getInstance(context: Context): AppInfoHolder {
            if (sInstance == null) {
                synchronized(AppInfoHolder::class.java) {
                    if (sInstance == null) {
                        sInstance = AppInfoHolder(context)
                    }
                }
            }
            return sInstance!!
        }

        @JvmStatic
        fun getSharedPreferences(context: Context): SharedPreferences {
            return context.getSharedPreferences(PushConstants.SP_NAME_MIPUSH, 0)
        }
    }

    init {
        init()
    }
}
