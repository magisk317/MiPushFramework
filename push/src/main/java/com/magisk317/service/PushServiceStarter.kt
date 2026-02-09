package com.magisk317.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.elvishew.xlog.XLog
import com.magisk317.Global

object PushServiceStarter {
    private val logger = XLog.tag("PushServiceStarter").build()

    @JvmStatic
    fun start(context: Context, intent: Intent) {
        try {
            if (isXMPushServiceTarget(intent)) {
                XMPushServiceLifecycleBridge.recordPendingStart(intent)
            }
            val shouldUseForegroundStart = Global.ConfigCenter().shouldStartPushAsForegroundService &&
                (!isXMPushServiceTarget(intent) || XMPushServiceLifecycleBridge.canStartForegroundImmediately())
            if (shouldUseForegroundStart) {
                ContextCompat.startForegroundService(context, intent)
            } else {
                context.startService(intent)
            }
        } catch (t: Throwable) {
            logger.e("failed to start service: ${intent.component}", t)
        }
    }

    private fun isXMPushServiceTarget(intent: Intent): Boolean {
        return intent.component?.className == "com.xiaomi.push.service.XMPushService"
    }
}
