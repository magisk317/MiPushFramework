package com.xiaomi.mipush.sdk

import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.PushConstants

object COSPushHelper {
    private const val TAG = "COSPushHelper"
    @Volatile
    private var mNeedRegister = false
    private var mLastTime: Long = 0

    fun convertMessage(intent: Intent) {
        AssemblePushHelper.convertMessage(intent)
    }

    fun doInNetworkChange(context: Context) {
        val jElapsedRealtime = SystemClock.elapsedRealtime()
        if (getNeedRegister()) {
            val j = mLastTime
            if (j <= 0 || j + Constants.ASSEMBLE_PUSH_NETWORK_INTERVAL <= jElapsedRealtime) {
                mLastTime = jElapsedRealtime
                registerCOSAssemblePush(context)
            }
        }
    }

    fun getNeedRegister(): Boolean = mNeedRegister

    fun hasNetwork(context: Context): Boolean = AssemblePushHelper.hasNetwork(context)

    fun onNotificationMessageCome(context: Context, str: String) {
    }

    fun onPassThoughMessageCome(context: Context, str: String) {
    }

    fun registerCOSAssemblePush(context: Context) {
        val manager = AssemblePushCollectionsManager.getInstance(context).getManager(AssemblePush.ASSEMBLE_PUSH_COS)
        if (manager != null) {
            MyLog.w("ASSEMBLE_PUSH :  register cos when network change!")
            manager.register()
        }
    }

    fun setNeedRegister(value: Boolean) {
        synchronized(COSPushHelper::class.java) {
            mNeedRegister = value
        }
    }

    fun uploadToken(context: Context, token: String) {
        AssemblePushHelper.uploadToken(context, AssemblePush.ASSEMBLE_PUSH_COS, token)
    }
}
