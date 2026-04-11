package com.xiaomi.push.service

import com.xiaomi.channel.commonutils.logger.MyLog

class PushClientNotifyJob(
    private val info: PushClientsManager.ClientLoginInfo,
) : XMPushService.Job(0) {
    var errorType: String? = null
    var notifyType: Int = 0
    var reason: Int = 0
    var reasonMessage: String? = null

    fun build(
        notifyType: Int,
        reason: Int,
        reasonMessage: String?,
        errorType: String?,
    ): XMPushService.Job {
        this.notifyType = notifyType
        this.reason = reason
        this.errorType = errorType
        this.reasonMessage = reasonMessage
        return this
    }

    override fun getDesc(): String = "notify job"

    override fun process() {
        if (info.shouldNotifyClient(notifyType, reason, errorType)) {
            info.notifyClientStatus(notifyType, reason, reasonMessage, errorType)
            return
        }
        MyLog.i(" ignore notify client :${info.chid}")
    }
}
