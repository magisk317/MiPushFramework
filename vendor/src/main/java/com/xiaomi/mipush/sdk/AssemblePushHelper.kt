package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import co.touchlab.kermit.Logger
import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import com.xiaomi.channel.commonutils.android.SharedPrefsCompat
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.PushConstants
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * Current override reference: com.xiaomi.xmsf 0.3.17-20260410000745 (versionCode 1003003000),
 * base.apk sha256 f3d72b6f5e1427ceecd3147a051d58e4dc95bb528397d486658e01cad9f7e590,
 * JADX path: com.xiaomi.xmsf/current/base/sources/com/xiaomi/mipush/sdk/AssemblePushHelper.java
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
object AssemblePushHelper {
    private const val COS_PUSH_ERROR = "cos_push_error"
    private const val COS_PUSH_TOKEN = "cos_push_token"
    private const val FCM_PUSH_ERROR = "fcm_push_error"
    private const val FCM_PUSH_TOKEN = "fcm_push_token"
    private const val FTOS_PUSH_ERROR = "ftos_push_error"
    private const val FTOS_PUSH_TOKEN = "ftos_push_token"
    private const val HMS_PUSH_ERROR = "hms_push_error"
    private const val HMS_PUSH_TOKEN = "hms_push_token"
    const val HMS_NOTIFICATION_CONTENT = "pushMsg"
    const val HMS_PASS_MESSAGE_CONTENT = "content"
    private const val KEY_ALIAS = "alias"
    private const val KEY_CATEGORY = "category"
    private const val KEY_CONTENT = "content"
    private const val KEY_DESC = "description"
    private const val KEY_EXTRA = "extra"
    private const val KEY_MESSAGE_ID = "messageId"
    private const val KEY_MESSAGE_TYPE = "messageType"
    private const val KEY_NOTIFIED = "isNotified"
    private const val KEY_NOTIFY_ID = "notifyId"
    private const val KEY_NOTIFY_TYPE = "notifyType"
    private const val KEY_PASS_THROUGH = "passThrough"
    private const val KEY_TITLE = "title"
    private const val KEY_TOPIC = "topic"
    private const val KEY_USER_ACCOUNT = "user_account"

    private val mTokens = HashMap<String, String>()

    @JvmStatic
    fun checkAssemblePushStatus(context: Context) {
        val sharedPreferences = context.getSharedPreferences("mipush_extra", 0)
        val tokenKey = getTokenKey(AssemblePush.ASSEMBLE_PUSH_HUAWEI)
        val tokenKey2 = getTokenKey(AssemblePush.ASSEMBLE_PUSH_FCM)
        val z = !sharedPreferences.getString(tokenKey, "").isNullOrEmpty() && sharedPreferences.getString(tokenKey2, "").isNullOrEmpty()
        if (z && tokenKey != null) {
            PushServiceClient.getInstance(context).send3rdPushHint(2, tokenKey)
        }
    }

    @JvmStatic
    fun getAssemblePush(intent: Intent?): AssemblePush {
        if (intent == null || intent.extras == null) {
            return AssemblePush.ASSEMBLE_PUSH_HUAWEI
        }
        val extras = intent.extras ?: return AssemblePush.ASSEMBLE_PUSH_HUAWEI
        if (extras.getString("pushMsg") != null) {
            return AssemblePush.ASSEMBLE_PUSH_HUAWEI
        }
        if (extras.getString(KEY_MESSAGE_ID) != null) {
            return AssemblePush.ASSEMBLE_PUSH_FCM
        }
        if (extras.getString(COS_PUSH_TOKEN) != null) {
            return AssemblePush.ASSEMBLE_PUSH_COS
        }
        if (extras.getString(FTOS_PUSH_TOKEN) != null) {
            return AssemblePush.ASSEMBLE_PUSH_FTOS
        }
        return AssemblePush.ASSEMBLE_PUSH_HUAWEI
    }

    @JvmStatic
    fun clearToken(context: Context, assemblePush: AssemblePush) {
        val tokenKey = getTokenKey(assemblePush)
        if (tokenKey.isNullOrEmpty()) return
        SharedPrefsCompat.apply(context.getSharedPreferences("mipush_extra", 0).edit().putString(tokenKey, ""))
    }

    @JvmStatic
    fun convertMessage(intent: Intent) {
        val extras = intent.extras ?: return
        if (!extras.containsKey(HMS_NOTIFICATION_CONTENT)) return
        val hmsContent = extras.getString(HMS_NOTIFICATION_CONTENT)
        if (hmsContent != null) {
            intent.putExtra(PushMessageHelper.KEY_MESSAGE, parseMiPushMessage(hmsContent))
        }
    }

    @JvmStatic
    @Throws(PackageManager.NameNotFoundException::class)
    fun getAssemblePushExtra(context: Context, assemblePush: AssemblePush): HashMap<String, String> {
        val map = HashMap<String, String>()
        val tokenKey = getTokenKey(assemblePush)
        if (tokenKey.isNullOrEmpty()) return map
        val str = when (assemblePush) {
            AssemblePush.ASSEMBLE_PUSH_HUAWEI -> {
                var i = -1
                try {
                    val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 128)
                    i = applicationInfo.metaData.getInt(Constants.HUAWEI_HMS_CLIENT_APPID)
                } catch (e: Exception) {
                    Logger.e(e) { "Failed to get HUAWEI_HMS_CLIENT_APPID" }
                }
                val brandName = AssemblePushUtils.getPhoneBrand(context).name
                val token = getAssemblePushToken(context, assemblePush)
                "brand:$brandName${Constants.WAVE_SEPARATOR}token:$token${Constants.WAVE_SEPARATOR}package_name:${context.packageName}${Constants.WAVE_SEPARATOR}app_id:$i"
            }
            AssemblePush.ASSEMBLE_PUSH_FCM -> "brand:${PhoneBrand.FCM.name}${Constants.WAVE_SEPARATOR}token:${getAssemblePushToken(context, assemblePush)}${Constants.WAVE_SEPARATOR}package_name:${context.packageName}"
            AssemblePush.ASSEMBLE_PUSH_COS -> "brand:${PhoneBrand.OPPO.name}${Constants.WAVE_SEPARATOR}token:${getAssemblePushToken(context, assemblePush)}${Constants.WAVE_SEPARATOR}package_name:${context.packageName}"
            AssemblePush.ASSEMBLE_PUSH_FTOS -> "brand:${PhoneBrand.VIVO.name}${Constants.WAVE_SEPARATOR}token:${getAssemblePushToken(context, assemblePush)}${Constants.WAVE_SEPARATOR}package_name:${context.packageName}"
        }
        map[Constants.ASSEMBLE_PUSH_REG_INFO] = str
        return map
    }

    @JvmStatic
    fun getAssemblePushToken(context: Context, assemblePush: AssemblePush): String? {
        val tokenKey = getTokenKey(assemblePush)
        if (tokenKey.isNullOrEmpty()) return null
        return synchronized(this) {
            mTokens[tokenKey] ?: context.getSharedPreferences("mipush_extra", 0).getString(tokenKey, "")
        }
    }

    @JvmStatic
    fun getAssemblePushTokenKey(assemblePush: AssemblePush): String? {
        return getTokenKey(assemblePush)
    }

    @JvmStatic
    fun getMiPushReceiver(context: Context): PushMessageReceiver? {
        val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
            setPackage(context.packageName)
        }
        return try {
            val list = context.packageManager.queryBroadcastReceivers(intent, 32)
            var next: android.content.pm.ResolveInfo? = null
            for (item in list) {
                if (item.activityInfo != null && item.activityInfo.packageName == context.packageName) {
                    next = item
                    break
                }
            }
            if (next != null) {
                SystemUtils.loadClass(context, next.activityInfo.name).getDeclaredConstructor().newInstance() as PushMessageReceiver
            } else {
                null
            }
        } catch (e: Exception) {
            Logger.e(e) { "Failed to get MiPushReceiver" }
            null
        }
    }

    @JvmStatic
    fun getSPErrorKey(assemblePush: AssemblePush): String? {
        return when (assemblePush) {
            AssemblePush.ASSEMBLE_PUSH_HUAWEI -> HMS_PUSH_ERROR
            AssemblePush.ASSEMBLE_PUSH_FCM -> FCM_PUSH_ERROR
            AssemblePush.ASSEMBLE_PUSH_COS -> COS_PUSH_ERROR
            AssemblePush.ASSEMBLE_PUSH_FTOS -> FTOS_PUSH_ERROR
        }
    }

    @JvmStatic
    fun getTokenKey(assemblePush: AssemblePush): String? {
        return when (assemblePush) {
            AssemblePush.ASSEMBLE_PUSH_HUAWEI -> HMS_PUSH_TOKEN
            AssemblePush.ASSEMBLE_PUSH_FCM -> FCM_PUSH_TOKEN
            AssemblePush.ASSEMBLE_PUSH_COS -> COS_PUSH_TOKEN
            AssemblePush.ASSEMBLE_PUSH_FTOS -> FTOS_PUSH_TOKEN
        }
    }

    @JvmStatic
    fun hasNetwork(context: Context?): Boolean {
        if (context == null) return false
        return Network.hasNetwork(context)
    }

    @JvmStatic
    fun isOpenAssemblePushOnlineSwitch(context: Context, assemblePush: AssemblePush): Boolean {
        val configKey = AssemblePushInfoHelper.getConfigKeyByType(assemblePush)
        return OnlineConfig.getInstance(context).getBooleanValue(configKey.value, true)
    }

    @JvmStatic
    fun parseMiPushMessage(str: String): MiPushMessage {
        val miPushMessage = MiPushMessage()
        if (str.isNotEmpty()) {
            try {
                val jSONObject = Json.parseToJsonElement(str).jsonObject
                var firstFieldError: Exception? = null

                fun <T> readField(block: () -> T): T? = try {
                    block()
                } catch (e: Exception) {
                    if (firstFieldError == null) {
                        firstFieldError = e
                    }
                    null
                }

                readField { jSONObject[KEY_MESSAGE_ID]?.jsonPrimitive?.content }
                    ?.let { miPushMessage.messageId = it }
                readField { jSONObject[KEY_DESC]?.jsonPrimitive?.content }
                    ?.let { miPushMessage.description = it }
                readField { jSONObject[KEY_TITLE]?.jsonPrimitive?.content }
                    ?.let { miPushMessage.title = it }
                readField { jSONObject[KEY_CONTENT]?.jsonPrimitive?.content }
                    ?.let { miPushMessage.content = it }
                readField { jSONObject[KEY_PASS_THROUGH]?.jsonPrimitive?.intOrNull }
                    ?.let { miPushMessage.passThrough = it }
                readField { jSONObject[KEY_NOTIFY_TYPE]?.jsonPrimitive?.intOrNull }
                    ?.let { miPushMessage.notifyType = it }
                readField { jSONObject[KEY_MESSAGE_TYPE]?.jsonPrimitive?.intOrNull }
                    ?.let { miPushMessage.messageType = it }
                readField { jSONObject[KEY_ALIAS]?.jsonPrimitive?.content }
                    ?.let { miPushMessage.alias = it }
                readField { jSONObject[KEY_TOPIC]?.jsonPrimitive?.content }
                    ?.let { miPushMessage.topic = it }
                readField { jSONObject[KEY_USER_ACCOUNT]?.jsonPrimitive?.content }
                    ?.let { miPushMessage.userAccount = it }
                readField { jSONObject[KEY_NOTIFY_ID]?.jsonPrimitive?.intOrNull }
                    ?.let { miPushMessage.notifyId = it }
                readField { jSONObject[KEY_CATEGORY]?.jsonPrimitive?.content }
                    ?.let { miPushMessage.category = it }
                readField { jSONObject[KEY_NOTIFIED]?.jsonPrimitive?.booleanOrNull }
                    ?.let { miPushMessage.isNotified = it }
                readField { jSONObject[KEY_EXTRA]?.jsonObject }?.let { jSONObject2 ->
                    val map = HashMap<String, String>()
                    for ((k, v) in jSONObject2) {
                        readField { v.jsonPrimitive.content }?.let { map[k] = it }
                    }
                    if (map.isNotEmpty()) {
                        miPushMessage.extra = map
                    }
                }

                firstFieldError?.let { error ->
                    Logger.e(error) { "Failed to parse MiPushMessage" }
                }
            } catch (e: Exception) {
                Logger.e(e) { "Failed to parse MiPushMessage" }
            }
        }
        return miPushMessage
    }

    @JvmStatic
    fun registerAssemblePush(context: Context) {
        AssemblePushCollectionsManager.getInstance(context).register()
    }

    @JvmStatic
    fun reportError(str: String, i: Int) {
        MiTinyDataClient.upload(HMS_PUSH_ERROR, str, 1L, "error code = $i")
    }

    private fun saveAssemblePushToken(context: Context, assemblePush: AssemblePush, token: String) {
        synchronized(this) {
            val tokenKey = getTokenKey(assemblePush)
            if (tokenKey.isNullOrEmpty()) {
                Logger.w { "ASSEMBLE_PUSH : can not find the key of token used in sp file" }
                return
            }
            SharedPrefsCompat.apply(context.getSharedPreferences("mipush_extra", 0).edit().putString(tokenKey, token))
            Logger.w { "ASSEMBLE_PUSH : update sp file success! token=${DefaultLogSanitizer.redactArg(token)}" }
        }
    }

    @JvmStatic
    fun saveAssemblePushTokenAfterAck(context: Context, assemblePush: AssemblePush, token: String) {
        ScheduledJobManager.getInstance(context).addOneShootJob {
            if (token.isEmpty()) return@addOneShootJob
            val strArrSplit = token.split(Constants.WAVE_SEPARATOR)
            var strSubstring = ""
            for (str2 in strArrSplit) {
                if (str2.isNotEmpty() && str2.startsWith("token:")) {
                    strSubstring = str2.substring(str2.indexOf(":") + 1)
                    break
                }
            }
            if (strSubstring.isEmpty()) {
                Logger.w { "ASSEMBLE_PUSH : receive incorrect token" }
                return@addOneShootJob
            }
            Logger.w { "ASSEMBLE_PUSH : receive correct token" }
            saveAssemblePushToken(context, assemblePush, strSubstring)
            checkAssemblePushStatus(context)
        }
    }

    private fun saveAssembleToken(assemblePush: AssemblePush, token: String) {
        synchronized(this) {
            val tokenKey = getTokenKey(assemblePush)
            if (tokenKey.isNullOrEmpty()) {
                Logger.w { "ASSEMBLE_PUSH : can not find the key of token used in sp file" }
            } else if (token.isEmpty()) {
                Logger.w { "ASSEMBLE_PUSH : token is null" }
            } else {
                mTokens[tokenKey] = token
            }
        }
    }

    @JvmStatic
    fun unregisterAssemblePush(context: Context) {
        AssemblePushCollectionsManager.getInstance(context).unregister()
    }

    @JvmStatic
    fun uploadToken(context: Context, assemblePush: AssemblePush, token: String) {
        if (token.isEmpty()) return
        val sharedPreferences = context.getSharedPreferences("mipush_extra", 0)
        val tokenKey = getTokenKey(assemblePush)
        if (tokenKey.isNullOrEmpty()) {
            Logger.w { "ASSEMBLE_PUSH : can not find the key of token used in sp file" }
            return
        }
        val string = sharedPreferences.getString(tokenKey, "")
        if (!string.isNullOrEmpty() && token == string) {
            Logger.w { "ASSEMBLE_PUSH : do not need to send token" }
            return
        }
        Logger.w { "ASSEMBLE_PUSH : send token upload" }
        saveAssembleToken(assemblePush, token)
        val retryType = AssemblePushInfoHelper.getRetryType(assemblePush) ?: return
        PushServiceClient.getInstance(context).sendAssemblePushTokenCommon(null, retryType, assemblePush)
    }
}
