package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

class XMPushServiceChannelSupport private constructor() {
    companion object {
        @JvmStatic
        fun closeAllChannelByChid(service: XMPushService, chid: String, reason: Int) {
            PushClientsManager.getInstance().getAllClientLoginInfoByChid(chid).forEach { clientLoginInfo ->
                service.executeJob(UnbindJob(service, clientLoginInfo, reason, null, null))
            }
            PushClientsManager.getInstance().deactivateAllClientByChid(chid)
        }
    }
}
