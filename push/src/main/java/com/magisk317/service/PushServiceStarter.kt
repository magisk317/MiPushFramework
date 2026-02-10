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
            val isXmPushServiceTarget = isXMPushServiceTarget(intent)
            if (isXmPushServiceTarget) {
                XMPushServiceLifecycleBridge.recordPendingStart(intent)
            }
            // The MiPush service is expected to foreground itself via lifecycle callbacks.
            // Starting it with startForegroundService has caused repeated 5s contract ANRs on some ROMs.
            if (isXmPushServiceTarget) {
                context.startService(intent)
                logger.d("startService target=XMPushService component=${intent.component}")
                return
            }

            val shouldUseForegroundStart = Global.ConfigCenter().shouldStartPushAsForegroundService &&
                XMPushServiceLifecycleBridge.canStartForegroundImmediately()
            if (shouldUseForegroundStart) {
                ContextCompat.startForegroundService(context, intent)
                logger.d("startForegroundService component=${intent.component}")
            } else {
                context.startService(intent)
                logger.d("startService component=${intent.component}")
            }
        } catch (t: Throwable) {
            logger.e("failed to start service: ${intent.component}", t)
        }
    }

    private fun isXMPushServiceTarget(intent: Intent): Boolean {
        return intent.component?.className == "com.xiaomi.push.service.XMPushService"
    }
}
