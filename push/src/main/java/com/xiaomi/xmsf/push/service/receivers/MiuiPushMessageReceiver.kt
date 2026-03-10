package com.xiaomi.xmsf.push.service.receivers

import android.content.Context
import android.content.Intent
import com.magisk317.diagnostics.RateLimitedWarnLogger
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.mipush.sdk.MiPushCommandMessage
import com.xiaomi.mipush.sdk.MiPushMessage
import com.xiaomi.mipush.sdk.PushMessageReceiver
import com.xiaomi.xmsf.push.service.XMAccountManager

class MiuiPushMessageReceiver : PushMessageReceiver() {
    private val TAG = MiuiPushMessageReceiver::class.java.simpleName
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = TAG)
        fun i(msg: String) = Napier.i(msg, tag = TAG)
        fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG)
    }

    override fun onCommandResult(context: Context, miPushCommandMessage: MiPushCommandMessage) {
        logger.d("onCommandResult")
        logger.d(miPushCommandMessage.toString())
        if (miPushCommandMessage.resultCode.toInt() == 0) {
            val command = miPushCommandMessage.command
            if (miPushCommandMessage.commandArguments?.isNotEmpty() == true && "register" == command) {
                XMAccountManager.getInstance(context).setAccountAsAlias()
            }
            return
        }
        logger.e(miPushCommandMessage.toString())
    }

    override fun onReceivePassThroughMessage(context: Context, miPushMessage: MiPushMessage) {
        routeIncomingMessage(context, miPushMessage, isNotified = false)
    }

    override fun onNotificationMessageClicked(context: Context, miPushMessage: MiPushMessage) {
        routeIncomingMessage(context, miPushMessage, isNotified = true)
    }

    private fun routeIncomingMessage(context: Context, miPushMessage: MiPushMessage, isNotified: Boolean) {
        logger.i("onReceiveMessage -> $miPushMessage")
        val pkg = miPushMessage.extra["miui_package_name"]
        if (!pkg.isNullOrBlank()) {
            logger.d("not empty")
            val intent = Intent()
            intent.setPackage(pkg)
            intent.putExtras(miPushMessage.toBundle())
                if (isNotified) {
                    logger.d("isNotified -> true")
                    intent.action = "com.xiaomi.mipush.miui.CLICK_MESSAGE"
                    runCatching { context.startService(intent) }
                        .onFailure {
                            RateLimitedWarnLogger.warn(
                                logTag = TAG,
                                key = "startService:$pkg",
                                message = "failed to forward clicked notification",
                                throwable = it
                            )
                        }
                } else {
                    logger.d("send broadcast")
                    intent.action = "com.xiaomi.mipush.miui.RECEIVE_MESSAGE"
                    runCatching { context.sendBroadcast(intent) }
                        .onFailure {
                            RateLimitedWarnLogger.warn(
                                logTag = TAG,
                                key = "sendBroadcast:$pkg",
                                message = "failed to forward passthrough message",
                                throwable = it
                            )
                        }
                }
            }
    }
}
