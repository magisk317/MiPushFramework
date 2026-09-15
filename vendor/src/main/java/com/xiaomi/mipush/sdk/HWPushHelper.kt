package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.logger.KermitLoggerCompat
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/*
 * No stock 7.4.67-C same-path source was found in the split source tree.
 */
object HWPushHelper {
    private const val LAST_CONNECT_TIME = "last_connect_time"
    private const val LAST_GET_TOKEN_TIME = "last_get_token_time"
    @Volatile private var isFailed = false

    fun convertMessage(intent: Intent) {
        AssemblePushHelper.convertMessage(intent)
    }

    fun hasNetwork(context: Context): Boolean = AssemblePushHelper.hasNetwork(context)

    fun isHmsTokenSynced(context: Context): Boolean {
        val assemblePush = AssemblePush.ASSEMBLE_PUSH_HUAWEI
        val tokenKey = AssemblePushHelper.getTokenKey(assemblePush)
        if (tokenKey.isNullOrEmpty()) return false
        val assemblePushToken = AssemblePushHelper.getAssemblePushToken(context, assemblePush)
        val syncStatus = OperatePushHelper.getInstance(context).getSyncStatus(RetryType.UPLOAD_HUAWEI_TOKEN)
        return !(assemblePushToken.isNullOrEmpty() || syncStatus.isNullOrEmpty() || OperatePushHelper.SYNCED != syncStatus)
    }

    fun isUserOpenHmsPush(context: Context): Boolean = MiPushClient.getOpenHmsPush(context)

    fun needConnect(): Boolean = isFailed

    fun notifyHmsNotificationMessageClicked(context: Context, data: String?) {
        var str2 = ""
        if (!data.isNullOrEmpty()) {
            try {
                val jSONArray = Json.parseToJsonElement(data).jsonArray
                for (element in jSONArray) {
                    val jSONObject = element.jsonObject
                    val pushMsg = jSONObject["pushMsg"]?.jsonPrimitive?.content
                    if (pushMsg != null) {
                        str2 = pushMsg
                        break
                    }
                }
            } catch (e: Exception) {
                KermitLoggerCompat.e("Failed to parse HMS notification message", e)
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
            if (!data.isNullOrEmpty()) {
                val jSONObject = Json.parseToJsonElement(data).jsonObject
                string = jSONObject["content"]?.jsonPrimitive?.content.orEmpty()
            }
        } catch (e: Exception) {
            KermitLoggerCompat.e("Failed to parse HMS pass-through message", e)
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
