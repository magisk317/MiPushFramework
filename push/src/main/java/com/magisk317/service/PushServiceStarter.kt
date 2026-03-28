package com.magisk317.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import io.github.aakira.napier.Napier
import com.magisk317.Global
import com.xiaomi.xmsf.runtime.PushRuntimeComponents
import kotlinx.coroutines.runBlocking

object PushServiceStarter {
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = "PushServiceStarter")
        fun e(msg: String, t: Throwable) = Napier.e(msg, t, tag = "PushServiceStarter")
    }

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

            val shouldUseForegroundStart = runBlocking { Global.ConfigCenter().shouldStartPushAsForegroundServiceAsync() } &&
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
        return intent.component?.className == PushRuntimeComponents.LEGACY_MAIN_SERVICE_CLASS
    }
}
