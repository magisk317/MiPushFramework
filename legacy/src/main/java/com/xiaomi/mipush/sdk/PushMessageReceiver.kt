package com.xiaomi.mipush.sdk
import io.github.magisk317.mipush.protocol.model.*

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.clientReport.PushClientReportManager
import com.xiaomi.push.service.clientReport.ReportConstants

abstract class PushMessageReceiver : BroadcastReceiver() {
    open fun onCommandResult(context: Context, miPushCommandMessage: MiPushCommandMessage) {
    }

    open fun onNotificationMessageArrived(context: Context, miPushMessage: MiPushMessage) {
    }

    open fun onNotificationMessageClicked(context: Context, miPushMessage: MiPushMessage) {
    }

    override fun onReceive(context: Context, intent: Intent) {
        MessageHandleService.addJob(context.applicationContext, MessageHandleService.MessageHandleJob(intent, this))
        try {
            val intExtra = intent.getIntExtra(ReportConstants.EVENT_MESSAGE_TYPE, -1)
            if (intExtra == 2000) {
                PushClientReportManager.getInstance(context.applicationContext).reportEvent(
                    context.packageName, intent, ReportConstants.THROUGH_TYPE_RECEIVE_RECEIVE_BROADCAST, null
                )
            } else if (intExtra == 6000) {
                PushClientReportManager.getInstance(context.applicationContext).reportEvent(
                    context.packageName, intent, ReportConstants.REGISTER_TYPE_APP_RECEIVE_BROADCAST, null
                )
            }
        } catch (e: Exception) {
            MyLog.e(e)
        }
    }

    open fun onReceiveMessage(context: Context, miPushMessage: MiPushMessage) {
    }

    open fun onReceivePassThroughMessage(context: Context, miPushMessage: MiPushMessage) {
    }

    open fun onReceiveRegisterResult(context: Context, miPushCommandMessage: MiPushCommandMessage) {
    }

    open fun onRequirePermissions(context: Context, permissions: Array<String>) {
    }
}
