package io.github.magisk317.mipush.service

import com.xiaomi.push.service.PullAllApplicationDataFromServerJob
import com.xiaomi.push.service.XMPushServiceCore

class PullAllApplicationDataAbility(
    private val pushService: XMPushServiceCore
) : XMPushServiceListener {
    override fun connectionStatusChanged(connectionStatus: ConnectionStatus) {
        if (connectionStatus == ConnectionStatus.connected) {
            pushService.executeJob(PullAllApplicationDataFromServerJob(pushService))
        }
    }
}
