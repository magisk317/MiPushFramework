package com.magisk317.service

import com.xiaomi.push.service.PullAllApplicationDataFromServerJob
import com.xiaomi.push.service.XMPushService

class PullAllApplicationDataAbility(
    private val pushService: XMPushService
) : XMPushServiceListener {
    override fun connectionStatusChanged(connectionStatus: ConnectionStatus) {
        if (connectionStatus == ConnectionStatus.connected) {
            pushService.executeJob(PullAllApplicationDataFromServerJob(pushService))
        }
    }
}
