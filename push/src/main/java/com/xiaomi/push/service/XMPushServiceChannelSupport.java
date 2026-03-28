package com.xiaomi.push.service;

import java.util.Collection;

final class XMPushServiceChannelSupport {
    private XMPushServiceChannelSupport() {
    }

    static void closeAllChannelByChid(XMPushService service, String chid, int reason) {
        Collection<PushClientsManager.ClientLoginInfo> allClientLoginInfoByChid = PushClientsManager.getInstance().getAllClientLoginInfoByChid(chid);
        if (allClientLoginInfoByChid != null) {
            for (PushClientsManager.ClientLoginInfo clientLoginInfo : allClientLoginInfoByChid) {
                if (clientLoginInfo != null) {
                    service.executeJob(new UnbindJob(service, clientLoginInfo, reason, null, null));
                }
            }
        }
        PushClientsManager.getInstance().deactivateAllClientByChid(chid);
    }
}
