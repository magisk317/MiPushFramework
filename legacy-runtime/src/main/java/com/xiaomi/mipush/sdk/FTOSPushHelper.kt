package com.xiaomi.mipush.sdk

import com.xiaomi.channel.commonutils.logger.MyLog

import android.content.Context
import android.os.SystemClock
import android.text.TextUtils
import com.xiaomi.push.service.PushConstants

object FTOSPushHelper {
    private var mLastTime: Long = 0
    @Volatile
    private var mNeedRegister = false

    fun doInNetworkChange(context: Context) {
        val jElapsedRealtime = SystemClock.elapsedRealtime()
        if (getNeedRegister()) {
            val j = mLastTime
            if (j <= 0 || j + Constants.ASSEMBLE_PUSH_NETWORK_INTERVAL <= jElapsedRealtime) {
                mLastTime = jElapsedRealtime
                registerFTOSAssemblePush(context)
            }
        }
    }

    fun getNeedRegister(): Boolean = mNeedRegister

    fun hasNetwork(context: Context): Boolean = AssemblePushHelper.hasNetwork(context)

    fun notifyFTOSNotificationClicked(context: Context, map: Map<String, String>?) {
        if (map == null || !map.containsKey("pushMsg")) return
        val str = map["pushMsg"]
        if (TextUtils.isEmpty(str)) return
        val miPushReceiver = AssemblePushHelper.getMiPushReceiver(context) ?: return
        val miPushMessage = AssemblePushHelper.parseMiPushMessage(str!!)
        if (miPushMessage.extra?.containsKey(PushConstants.EXTRA_PARAM_NOTIFY_EFFECT) == true) return
        miPushReceiver.onNotificationMessageClicked(context, miPushMessage)
    }

    private fun registerFTOSAssemblePush(context: Context) {
        val manager = AssemblePushCollectionsManager.getInstance(context).getManager(AssemblePush.ASSEMBLE_PUSH_FTOS)
        if (manager != null) {
            MyLog.w("ASSEMBLE_PUSH :  register fun touch os when network change!")
            manager.register()
        }
    }

    fun setNeedRegister(value: Boolean) {
        mNeedRegister = value
    }

    fun uploadToken(context: Context, token: String) {
        AssemblePushHelper.uploadToken(context, AssemblePush.ASSEMBLE_PUSH_FTOS, token)
    }
}
