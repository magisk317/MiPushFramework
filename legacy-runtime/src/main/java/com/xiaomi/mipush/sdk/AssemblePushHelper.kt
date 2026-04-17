package com.xiaomi.mipush.sdk

import com.xiaomi.channel.commonutils.logger.MyLog

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Bundle
import android.text.TextUtils
import com.xiaomi.channel.commonutils.android.SharedPrefsCompat
import com.xiaomi.channel.commonutils.android.SystemUtils
import com.xiaomi.channel.commonutils.misc.ScheduledJobManager
import com.xiaomi.channel.commonutils.network.Network
import com.xiaomi.push.service.OnlineConfig
import com.xiaomi.push.service.PushConstants
import org.json.JSONObject

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
        val z = !TextUtils.isEmpty(sharedPreferences.getString(tokenKey, "")) && TextUtils.isEmpty(sharedPreferences.getString(tokenKey2, ""))
        if (z && tokenKey != null) {
            PushServiceClient.getInstance(context).send3rdPushHint(2, tokenKey)
        }
    }

    @JvmStatic
    fun clearToken(context: Context, assemblePush: AssemblePush) {
        val tokenKey = getTokenKey(assemblePush)
        if (TextUtils.isEmpty(tokenKey)) return
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
        if (TextUtils.isEmpty(tokenKey)) return map
        val str = when (assemblePush) {
            AssemblePush.ASSEMBLE_PUSH_HUAWEI -> {
                var i = -1
                try {
                    val applicationInfo = context.packageManager.getApplicationInfo(context.packageName, 128)
                    i = applicationInfo.metaData.getInt(Constants.HUAWEI_HMS_CLIENT_APPID)
                } catch (e: Exception) {
                    MyLog.e(e.toString())
                }
                val brandName = AssemblePushUtils.getPhoneBrand(context).name
                val token = if (tokenKey != null) getAssemblePushToken(context, tokenKey) else ""
                "brand:$brandName${Constants.WAVE_SEPARATOR}token:$token${Constants.WAVE_SEPARATOR}package_name:${context.packageName}${Constants.WAVE_SEPARATOR}app_id:$i"
            }
            AssemblePush.ASSEMBLE_PUSH_FCM -> "brand:${PhoneBrand.FCM.name}${Constants.WAVE_SEPARATOR}token:${if (tokenKey != null) getAssemblePushToken(context, tokenKey) else ""}${Constants.WAVE_SEPARATOR}package_name:${context.packageName}"
            AssemblePush.ASSEMBLE_PUSH_COS -> "brand:${PhoneBrand.OPPO.name}${Constants.WAVE_SEPARATOR}token:${if (tokenKey != null) getAssemblePushToken(context, tokenKey) else ""}${Constants.WAVE_SEPARATOR}package_name:${context.packageName}"
            AssemblePush.ASSEMBLE_PUSH_FTOS -> "brand:${PhoneBrand.VIVO.name}${Constants.WAVE_SEPARATOR}token:${if (tokenKey != null) getAssemblePushToken(context, tokenKey) else ""}${Constants.WAVE_SEPARATOR}package_name:${context.packageName}"
        }
        map[Constants.ASSEMBLE_PUSH_REG_INFO] = str
        return map
    }

    @JvmStatic
    fun getAssemblePushToken(context: Context, key: String): String {
        return synchronized(this) {
            mTokens[key] ?: ""
        }
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
            MyLog.e(e.toString())
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
        val configKey = AssemblePushInfoHelper.getConfigKeyByType(assemblePush) ?: return false
        return OnlineConfig.getInstance(context).getBooleanValue(configKey.value, true)
    }

    @JvmStatic
    fun parseMiPushMessage(str: String): MiPushMessage {
        val miPushMessage = MiPushMessage()
        if (!TextUtils.isEmpty(str)) {
            try {
                val jSONObject = JSONObject(str)
                if (jSONObject.has(KEY_MESSAGE_ID)) miPushMessage.messageId = jSONObject.getString(KEY_MESSAGE_ID)
                if (jSONObject.has(KEY_DESC)) miPushMessage.description = jSONObject.getString(KEY_DESC)
                if (jSONObject.has(KEY_TITLE)) miPushMessage.title = jSONObject.getString(KEY_TITLE)
                if (jSONObject.has(KEY_CONTENT)) miPushMessage.content = jSONObject.getString(KEY_CONTENT)
                if (jSONObject.has(KEY_PASS_THROUGH)) miPushMessage.passThrough = jSONObject.getInt(KEY_PASS_THROUGH)
                if (jSONObject.has(KEY_NOTIFY_TYPE)) miPushMessage.notifyType = jSONObject.getInt(KEY_NOTIFY_TYPE)
                if (jSONObject.has(KEY_MESSAGE_TYPE)) miPushMessage.messageType = jSONObject.getInt(KEY_MESSAGE_TYPE)
                if (jSONObject.has(KEY_ALIAS)) miPushMessage.alias = jSONObject.getString(KEY_ALIAS)
                if (jSONObject.has(KEY_TOPIC)) miPushMessage.topic = jSONObject.getString(KEY_TOPIC)
                if (jSONObject.has(KEY_USER_ACCOUNT)) miPushMessage.userAccount = jSONObject.getString(KEY_USER_ACCOUNT)
                if (jSONObject.has(KEY_NOTIFY_ID)) miPushMessage.notifyId = jSONObject.getInt(KEY_NOTIFY_ID)
                if (jSONObject.has(KEY_CATEGORY)) miPushMessage.category = jSONObject.getString(KEY_CATEGORY)
                if (jSONObject.has(KEY_NOTIFIED)) miPushMessage.isNotified = jSONObject.getBoolean(KEY_NOTIFIED)
                if (jSONObject.has(KEY_EXTRA)) {
                    val jSONObject2 = jSONObject.getJSONObject(KEY_EXTRA)
                    val map = HashMap<String, String>()
                    val keys = jSONObject2.keys()
                    while (keys.hasNext()) {
                        val key = keys.next()
                        map[key] = jSONObject2.getString(key)
                    }
                    if (map.isNotEmpty()) {
                        miPushMessage.extra = map
                    }
                }
            } catch (e: Exception) {
                MyLog.e(e.toString())
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
            if (TextUtils.isEmpty(tokenKey)) {
                MyLog.w("ASSEMBLE_PUSH : can not find the key of token used in sp file")
                return
            }
            SharedPrefsCompat.apply(context.getSharedPreferences("mipush_extra", 0).edit().putString(tokenKey, token))
            MyLog.w("ASSEMBLE_PUSH : update sp file success!  $token")
        }
    }

    @JvmStatic
    fun saveAssemblePushTokenAfterAck(context: Context, assemblePush: AssemblePush, token: String) {
        ScheduledJobManager.getInstance(context).addOneShootJob {
            if (TextUtils.isEmpty(token)) return@addOneShootJob
            val strArrSplit = token.split(Constants.WAVE_SEPARATOR)
            var strSubstring = ""
            for (str2 in strArrSplit) {
                if (!TextUtils.isEmpty(str2) && str2.startsWith("token:")) {
                    strSubstring = str2.substring(str2.indexOf(":") + 1)
                    break
                }
            }
            if (TextUtils.isEmpty(strSubstring)) {
                MyLog.w("ASSEMBLE_PUSH : receive incorrect token")
                return@addOneShootJob
            }
            MyLog.w("ASSEMBLE_PUSH : receive correct token")
            saveAssemblePushToken(context, assemblePush, strSubstring)
            checkAssemblePushStatus(context)
        }
    }

    private fun saveAssembleToken(assemblePush: AssemblePush, token: String) {
        synchronized(this) {
            val tokenKey = getTokenKey(assemblePush)
            if (TextUtils.isEmpty(tokenKey)) {
                MyLog.w("ASSEMBLE_PUSH : can not find the key of token used in sp file")
            } else if (TextUtils.isEmpty(token)) {
                MyLog.w("ASSEMBLE_PUSH : token is null")
            } else if (tokenKey != null) {
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
        if (TextUtils.isEmpty(token)) return
        val sharedPreferences = context.getSharedPreferences("mipush_extra", 0)
        val tokenKey = getTokenKey(assemblePush)
        if (TextUtils.isEmpty(tokenKey)) {
            MyLog.w("ASSEMBLE_PUSH : can not find the key of token used in sp file")
            return
        }
        val string = sharedPreferences.getString(tokenKey, "")
        if (!TextUtils.isEmpty(string) && token == string) {
            MyLog.w("ASSEMBLE_PUSH : do not need to send token")
            return
        }
        MyLog.w("ASSEMBLE_PUSH : send token upload")
        saveAssembleToken(assemblePush, token)
        val retryType = AssemblePushInfoHelper.getRetryType(assemblePush) ?: return
        PushServiceClient.getInstance(context).sendAssemblePushTokenCommon(null, retryType, assemblePush)
    }
}
