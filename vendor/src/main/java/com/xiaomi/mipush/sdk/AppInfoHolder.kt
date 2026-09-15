package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.SharedPreferences
import co.touchlab.kermit.Logger
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.DeviceInfo
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.PushVersionInfo
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.util.HashMap

/*
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
@android.annotation.SuppressLint("StaticFieldLeak")
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
            getSharedPreferences(mContext).edit().clear().apply()
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
            getSharedPreferences(mContext).edit().putBoolean(PREF_KEY_VALID, isValid).apply()
        }

        fun isVaild(): Boolean {
            return isVaild(appID, appToken)
        }

        fun isVaild(str: String?, str2: String?): Boolean {
            return appID == str &&
                appToken == str2 &&
                !regID.isNullOrEmpty() &&
                !regSecret.isNullOrEmpty() &&
                (
                    deviceId == DeviceInfo.getInstanceId(mContext) ||
                        deviceId == DeviceInfo.getSimpleDeviceId(mContext)
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
            editor.apply()
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
            editor.apply()
        }

        companion object {
            @JvmStatic
            fun parseClientInfoData(context: Context, str: String?): ClientInfoData? {
                if (str.isNullOrEmpty()) return null
                return try {
                    val jsonObject = Json.parseToJsonElement(str).jsonObject
                    ClientInfoData(context).apply {
                        appID = jsonObject[PREF_KEY_APP_ID]?.jsonPrimitive?.content
                        appToken = jsonObject[PREF_KEY_APP_TOKEN]?.jsonPrimitive?.content
                        regID = jsonObject[PREF_KEY_REG_ID]?.jsonPrimitive?.content
                        regSecret = jsonObject[PREF_KEY_REG_SECRET]?.jsonPrimitive?.content
                        deviceId = jsonObject[PREF_KEY_DEVICE_ID]?.jsonPrimitive?.content
                        versionName = jsonObject[PREF_KEY_VERSION_NAME]?.jsonPrimitive?.content
                        isValid = jsonObject[PREF_KEY_VALID]?.jsonPrimitive?.booleanOrNull ?: true
                        isPaused = jsonObject[PREF_KEY_PAUSED]?.jsonPrimitive?.booleanOrNull ?: false
                        envType = jsonObject[PREF_KEY_ENV_TYPE]?.jsonPrimitive?.intOrNull ?: 1
                        regResource = jsonObject[PREF_KEY_REG_RESOURCE]?.jsonPrimitive?.content
                    }
                } catch (throwable: Throwable) {
                    Logger.e(throwable) { "Failed to parse ClientInfoData" }
                    null
                }
            }

            @JvmStatic
            fun toString(clientInfoData: ClientInfoData): String? {
                return try {
                    buildJsonObject {
                        clientInfoData.appID?.let { put(PREF_KEY_APP_ID, it) }
                        clientInfoData.appToken?.let { put(PREF_KEY_APP_TOKEN, it) }
                        clientInfoData.regID?.let { put(PREF_KEY_REG_ID, it) }
                        clientInfoData.regSecret?.let { put(PREF_KEY_REG_SECRET, it) }
                        clientInfoData.deviceId?.let { put(PREF_KEY_DEVICE_ID, it) }
                        clientInfoData.versionName?.let { put(PREF_KEY_VERSION_NAME, it) }
                        put(PREF_KEY_VALID, clientInfoData.isValid)
                        put(PREF_KEY_PAUSED, clientInfoData.isPaused)
                        put(PREF_KEY_ENV_TYPE, clientInfoData.envType)
                        clientInfoData.regResource?.let { put(PREF_KEY_REG_RESOURCE, it) }
                    }.toString()
                } catch (throwable: Throwable) {
                    Logger.e(throwable) { "Failed to serialize ClientInfoData" }
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
        if (!mInfoData.deviceId.isNullOrEmpty() && DeviceInfo.startsWithDevPrefix(mInfoData.deviceId)) {
            mInfoData.deviceId = DeviceInfo.getInstanceId(mContext)
            sharedPreferences.edit().putString(PREF_KEY_DEVICE_ID, mInfoData.deviceId).apply()
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
        val deviceIdPresent = !mInfoData.deviceId.isNullOrEmpty()
        return "valid=${mInfoData.isValid}" +
            " appIdPresent=${!mInfoData.appID.isNullOrEmpty()}" +
            " appTokenPresent=${!mInfoData.appToken.isNullOrEmpty()}" +
            " appIdMatch=${mInfoData.appID == expectedAppId}" +
            " appTokenMatch=${mInfoData.appToken == expectedAppToken}" +
            " regIdPresent=${!mInfoData.regID.isNullOrEmpty()}" +
            " regSecretPresent=${!mInfoData.regSecret.isNullOrEmpty()}" +
            " deviceIdPresent=$deviceIdPresent" +
            " instanceDeviceMatch=${deviceIdPresent && mInfoData.deviceId == instanceId}" +
            " simpleDeviceMatch=${deviceIdPresent && mInfoData.deviceId == simpleDeviceId}" +
            " envType=${mInfoData.envType}" +
            " regionPresent=${!mInfoData.appRegion.isNullOrEmpty()}" +
            " requestIdPresent=${!appRegRequestId.isNullOrEmpty()}"
    }

    fun checkAppInfo(): Boolean {
        if (mInfoData.isVaild()) {
            return true
        }
        Logger.w { "Don't send message before initialization succeeded!" }
        return false
    }

    fun checkVersionNameChanged(): Boolean {
        return PushVersionInfo.reportedAppVersionName(mContext.packageName, AppInfoUtils.getVersionName(mContext, mContext.packageName)) != mInfoData.versionName
    }

    fun clear() {
        mInfoData.clear()
    }

    fun delHybridAppInfo(str: String) {
        mHybridAppInfoCache.remove(str)
        getSharedPreferences(mContext).edit().remove(PREF_KEY_HYBRID_APP_INFO_PREFIX + str).apply()
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
            str == hybridAppInfo.appID &&
            str2 == hybridAppInfo.appToken
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
            .apply()
    }

    fun setEnvType(i: Int) {
        mInfoData.setEnvType(i)
        getSharedPreferences(mContext).edit().putInt(PREF_KEY_ENV_TYPE, i).apply()
    }

    fun setPaused(z: Boolean) {
        mInfoData.setPaused(z)
        getSharedPreferences(mContext).edit().putBoolean(PREF_KEY_PAUSED, z).apply()
    }

    fun updateVersionName(str: String?) {
        getSharedPreferences(mContext).edit().putString(PREF_KEY_VERSION_NAME, str).apply()
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
