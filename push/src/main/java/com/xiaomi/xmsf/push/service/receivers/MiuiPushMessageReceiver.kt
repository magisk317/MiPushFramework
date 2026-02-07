@file:Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
package com.xiaomi.xmsf.push.service.receivers

import android.content.Context
import android.content.Intent
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.xiaomi.mipush.sdk.MiPushCommandMessage
import com.xiaomi.mipush.sdk.MiPushMessage
import com.xiaomi.mipush.sdk.PushMessageReceiver
import com.xiaomi.xmsf.push.service.XMAccountManager

class MiuiPushMessageReceiver : PushMessageReceiver() {
    private val logger: Logger = XLog.tag(MiuiPushMessageReceiver::class.java.simpleName).build()

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

    override fun onReceiveMessage(context: Context, miPushMessage: MiPushMessage) {
        logger.i("onReceiveMessage -> $miPushMessage")
        val pkg = miPushMessage.extra["miui_package_name"] as? String
        if (!pkg.isNullOrBlank()) {
            logger.d("not empty")
            val intent = Intent()
            intent.setPackage(pkg)
            intent.putExtras(miPushMessage.toBundle())
            if (miPushMessage.isNotified) {
                logger.d("isNotified -> true")
                intent.action = "com.xiaomi.mipush.miui.CLICK_MESSAGE"
                context.startService(intent)
            } else {
                logger.d("send broadcast")
                intent.action = "com.xiaomi.mipush.miui.RECEIVE_MESSAGE"
                context.sendBroadcast(intent)
            }
        }
    }
}
