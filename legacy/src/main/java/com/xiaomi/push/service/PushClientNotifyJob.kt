package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog

class PushClientNotifyJob(private val clientLoginInfo: PushClientsManager.ClientLoginInfo) :
    XMPushServiceJob(0) {
    private var errorType: String? = null
    private var reasonCode = 0
    private var reasonMessage: String? = null
    private var notifyType = 0

    fun build(type: Int, reasonCode: Int, reasonMessage: String?, errorType: String?): PushClientNotifyJob {
        this.notifyType = type
        this.reasonCode = reasonCode
        this.reasonMessage = reasonMessage
        this.errorType = errorType
        return this
    }

    override fun process() {
        val action = clientLoginInfo.getPushAction()
        if (action == null) {
            MyLog.e("notify job $notifyType error, push action is null")
            return
        }
        clientLoginInfo.notifyClientStatus(notifyType, reasonCode, reasonMessage, errorType)
    }

    override fun getDesc(): String = "notify job"
}
