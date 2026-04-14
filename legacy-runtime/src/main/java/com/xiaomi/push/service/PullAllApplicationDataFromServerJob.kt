package com.xiaomi.push.service

import android.text.TextUtils
import com.xiaomi.push.service.XMPushServiceJob.Companion.TYPE_SEND_MSG
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionNotification

class PullAllApplicationDataFromServerJob(xmPushService: XMPushService) :
    XMPushService.Job(TYPE_SEND_MSG) {

    private val xmPushActionOperator = XmPushActionOperator(xmPushService)

    override fun getDesc(): String = "pull all application data"

    override fun process() {
        val context = xmPushActionOperator.context ?: return
        val sp = context.getSharedPreferences("pref_registered_pkg_names", 0)
        for (entry in sp.all.entries) {
            val packageName = entry.key
            val appId = entry.value?.toString()
            if (TextUtils.`isEmpty`(appId)) {
                continue
            }
            xmPushActionOperator.sendMessage(
                MIPushHelper.generateRequestContainer(packageName, appId!!, getPullAction(appId), ActionType.Notification),
                packageName
            )
        }
    }

    companion object {
        @JvmStatic
        fun getPullAction(appId: String): XmPushActionNotification {
            val notification = XmPushActionNotification()
            notification.appId = appId
            notification.type = "pull"
            notification.id = "fake_pull_${appId}_${System.currentTimeMillis()}"
            notification.requireAck = false
            return notification
        }
    }
}
