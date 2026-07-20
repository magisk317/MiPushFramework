package com.xiaomi.push.service

class XMPushServiceChannelSupport private constructor() {
    companion object {
        @JvmStatic
        fun closeAllChannelByChid(service: XMPushServiceCore, chid: String, reason: Int) {
            PushClientsManager.getInstance().getAllClientLoginInfoByChid(chid).forEach { clientLoginInfo ->
                service.executeJob(UnbindJob(service, clientLoginInfo, reason, null, null))
            }
            PushClientsManager.getInstance().deactivateAllClientByChid(chid)
        }
    }
}
