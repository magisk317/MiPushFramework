package com.xiaomi.push.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

final class PushClientsStateSupport {
    private PushClientsStateSupport() {
    }

    static void notifyConnectionFailed(Iterable<HashMap<String, PushClientsManager.ClientLoginInfo>> iterable) {
        Iterator<HashMap<String, PushClientsManager.ClientLoginInfo>> it = iterable.iterator();
        while (it.hasNext()) {
            Iterator<PushClientsManager.ClientLoginInfo> it2 = it.next().values().iterator();
            while (it2.hasNext()) {
                it2.next().setStatus(PushClientsManager.ClientStatus.unbind, 1, 3, null, null);
            }
        }
    }

    static List<String> queryChannelIdByPackage(Iterable<HashMap<String, PushClientsManager.ClientLoginInfo>> iterable, String str) {
        ArrayList arrayList = new ArrayList();
        Iterator<HashMap<String, PushClientsManager.ClientLoginInfo>> it = iterable.iterator();
        while (it.hasNext()) {
            for (PushClientsManager.ClientLoginInfo clientLoginInfo : it.next().values()) {
                if (str.equals(clientLoginInfo.pkgName)) {
                    arrayList.add(clientLoginInfo.chid);
                }
            }
        }
        return arrayList;
    }

    static void resetAllClients(Iterable<HashMap<String, PushClientsManager.ClientLoginInfo>> iterable, int i) {
        Iterator<HashMap<String, PushClientsManager.ClientLoginInfo>> it = iterable.iterator();
        while (it.hasNext()) {
            Iterator<PushClientsManager.ClientLoginInfo> it2 = it.next().values().iterator();
            while (it2.hasNext()) {
                it2.next().setStatus(PushClientsManager.ClientStatus.unbind, 2, i, null, null);
            }
        }
    }
}
