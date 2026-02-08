package com.xiaomi.push.service

import android.text.TextUtils
import com.magisk317.XMPushUtils
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import top.trumeet.common.utils.Utils

class PullAllApplicationDataFromServerJob(xmPushService: XMPushService) :
    XMPushService.Job(XMPushService.Job.TYPE_SEND_MSG) {

    private val xmPushActionOperator = XmPushActionOperator(xmPushService)

    override fun getDesc(): String = "pull all application data"

    override fun process() {
        val sp = requireNotNull(Utils.getApplication())
            .getSharedPreferences("pref_registered_pkg_names", 0)
        for (entry in sp.all.entries) {
            val packageName = entry.key
            val appId = entry.value?.toString()
            if (TextUtils.isEmpty(appId)) {
                continue
            }
            xmPushActionOperator.sendMessage(
                XMPushUtils.packToContainer(getPullAction(appId!!), packageName),
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
