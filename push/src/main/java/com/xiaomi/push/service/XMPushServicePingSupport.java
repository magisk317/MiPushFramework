package com.xiaomi.push.service;

import java.util.ArrayList;
import java.util.Iterator;

final class XMPushServicePingSupport {
    private XMPushServicePingSupport() {
    }

    static void onPong(ArrayList<PingCallBack> pingCallBacks) {
        Iterator it = new ArrayList(pingCallBacks).iterator();
        while (it.hasNext()) {
            ((PingCallBack) it.next()).pingFollowUpAction();
        }
    }
}
