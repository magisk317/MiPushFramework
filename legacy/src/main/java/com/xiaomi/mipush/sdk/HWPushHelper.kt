package com.xiaomi.mipush.sdk
import io.github.magisk317.mipush.protocol.model.*

import com.xiaomi.channel.commonutils.logger.MyLog

import android.content.Context
import android.content.Intent
import android.text.TextUtils
import org.json.JSONArray
import org.json.JSONObject

object HWPushHelper {
    private const val LAST_CONNECT_TIME = "last_connect_time"
    private const val LAST_GET_TOKEN_TIME = "last_get_token_time"
    private var isFailed = false

    fun convertMessage(intent: Intent) {
        AssemblePushHelper.convertMessage(intent)
    }

    fun hasNetwork(context: Context): Boolean = AssemblePushHelper.hasNetwork(context)

    fun isHmsTokenSynced(context: Context): Boolean {
        val tokenKey = AssemblePushHelper.getTokenKey(AssemblePush.ASSEMBLE_PUSH_HUAWEI)
        if (TextUtils.isEmpty(tokenKey)) return false
        val assemblePushToken = AssemblePushHelper.getAssemblePushToken(context, tokenKey!!)
        val syncStatus = OperatePushHelper.getInstance(context).getSyncStatus(RetryType.UPLOAD_HUAWEI_TOKEN)
        return !(TextUtils.isEmpty(assemblePushToken) || TextUtils.isEmpty(syncStatus) || !OperatePushHelper.SYNCED.equals(syncStatus))
    }

    fun isUserOpenHmsPush(context: Context): Boolean = MiPushClient.getOpenHmsPush(context)

    fun needConnect(): Boolean = isFailed

    fun notifyHmsNotificationMessageClicked(context: Context, data: String?) {
        var str2 = ""
        if (!TextUtils.isEmpty(data)) {
            try {
                val jSONArray = JSONArray(data!!)
                for (i in 0 until jSONArray.length()) {
                    val jSONObject = jSONArray.getJSONObject(i)
                    if (jSONObject.has("pushMsg")) {
                        str2 = jSONObject.getString("pushMsg")
                        break
                    }
                }
            } catch (e: Exception) {
                MyLog.e(e.toString())
                str2 = ""
            }
        }
        val miPushReceiver = AssemblePushHelper.getMiPushReceiver(context) ?: return
        val miPushMessageParseMiPushMessage = AssemblePushHelper.parseMiPushMessage(str2)
        val extra = miPushMessageParseMiPushMessage.extra
        if (extra != null && extra.containsKey("notify_effect")) {
            return
        }
        miPushReceiver.onNotificationMessageClicked(context, miPushMessageParseMiPushMessage)
    }

    fun notifyHmsPassThoughMessageArrived(context: Context, data: String?) {
        var string = ""
        try {
            if (!TextUtils.isEmpty(data)) {
                val jSONObject = JSONObject(data!!)
                string = if (jSONObject.has("content")) jSONObject.getString("content") else ""
            }
        } catch (e: Exception) {
            MyLog.e(e.toString())
            string = ""
        }
        val miPushReceiver = AssemblePushHelper.getMiPushReceiver(context)
        miPushReceiver?.onReceivePassThroughMessage(context, AssemblePushHelper.parseMiPushMessage(string))
    }

    fun registerHuaWeiAssemblePush(context: Context) {
        val manager = AssemblePushCollectionsManager.getInstance(context).getManager(AssemblePush.ASSEMBLE_PUSH_HUAWEI)
        manager?.register()
    }

    fun reportError(str: String, i: Int) {
        AssemblePushHelper.reportError(str, i)
    }

    fun setConnectTime(context: Context) {
        synchronized(this) {
            context.getSharedPreferences("mipush_extra", 0).edit().putLong(LAST_CONNECT_TIME, System.currentTimeMillis()).commit()
        }
    }

    fun setGetTokenTime(context: Context) {
        synchronized(this) {
            context.getSharedPreferences("mipush_extra", 0).edit().putLong(LAST_GET_TOKEN_TIME, System.currentTimeMillis()).commit()
        }
    }

    fun setNeedConnect(value: Boolean) {
        isFailed = value
    }

    fun shouldGetToken(context: Context): Boolean {
        return synchronized(this) {
            try {
                Math.abs(System.currentTimeMillis() - context.getSharedPreferences("mipush_extra", 0).getLong(LAST_GET_TOKEN_TIME, -1L)) > 172800000
            } catch (th: Throwable) {
                throw th
            }
        }
    }

    fun shouldTryConnect(context: Context): Boolean {
        return synchronized(this) {
            try {
                Math.abs(System.currentTimeMillis() - context.getSharedPreferences("mipush_extra", 0).getLong(LAST_CONNECT_TIME, -1L)) > 5000
            } catch (th: Throwable) {
                throw th
            }
        }
    }

    fun uploadToken(context: Context, token: String) {
        AssemblePushHelper.uploadToken(context, AssemblePush.ASSEMBLE_PUSH_HUAWEI, token)
    }
}
