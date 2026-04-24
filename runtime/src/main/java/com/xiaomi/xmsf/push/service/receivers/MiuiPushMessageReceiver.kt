package com.xiaomi.xmsf.push.service.receivers

import android.content.Context
import android.content.Intent
import io.github.magisk317.mipush.diagnostics.RateLimitedWarnLogger
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.xiaomi.mipush.sdk.MiPushCommandMessage
import com.xiaomi.mipush.sdk.MiPushMessage
import com.xiaomi.mipush.sdk.PushMessageReceiver
import io.github.magisk317.mipush.runtime.PushRuntime
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
        if (miPushCommandMessage.getResultCode().toInt() == 0) {
            val command = miPushCommandMessage.getCommand()
            if (miPushCommandMessage.getCommandArguments()?.isNotEmpty() == true && "register" == command) {
                PushRuntime.observeAccountEvent(
                    action = "register_command_result",
                    source = "MiuiPushMessageReceiver.onCommandResult"
                )
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
        val pkg = miPushMessage.extra?.get("miui_package_name")
        if (!pkg.isNullOrBlank()) {
            PushRuntime.observeNotificationEvent(
                packageName = pkg,
                action = if (isNotified) "miui_click_message" else "miui_receive_message",
                source = "MiuiPushMessageReceiver.routeIncomingMessage"
            )
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
