package com.xiaomi.push.service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

final class PushClientsCollection {
    private final ConcurrentHashMap<String, HashMap<String, PushClientsManager.ClientLoginInfo>> activeClients = new ConcurrentHashMap<>();
    private final List<PushClientsManager.ClientChangeListener> clientChangeListeners = new ArrayList();

    void addActiveClient(PushClientsManager.ClientLoginInfo clientLoginInfo) {
        HashMap<String, PushClientsManager.ClientLoginInfo> map = this.activeClients.get(clientLoginInfo.chid);
        if (map == null) {
            map = new HashMap<>();
            this.activeClients.put(clientLoginInfo.chid, map);
        }
        map.put(PushClientsManager.getSmtpLocalPart(clientLoginInfo.userId), clientLoginInfo);
        notifyListeners();
    }

    void addClientChangeListener(PushClientsManager.ClientChangeListener clientChangeListener) {
        this.clientChangeListeners.add(clientChangeListener);
    }

    void deactivateAllClientByChid(String str) {
        HashMap<String, PushClientsManager.ClientLoginInfo> map = this.activeClients.get(str);
        if (map != null) {
            Iterator<PushClientsManager.ClientLoginInfo> it = map.values().iterator();
            while (it.hasNext()) {
                it.next().unwatch();
            }
            map.clear();
            this.activeClients.remove(str);
        }
        notifyListeners();
    }

    void deactivateClient(String str, String str2) {
        HashMap<String, PushClientsManager.ClientLoginInfo> map = this.activeClients.get(str);
        if (map != null) {
            PushClientsManager.ClientLoginInfo clientLoginInfo = map.get(PushClientsManager.getSmtpLocalPart(str2));
            if (clientLoginInfo != null) {
                clientLoginInfo.unwatch();
            }
            map.remove(PushClientsManager.getSmtpLocalPart(str2));
            if (map.isEmpty()) {
                this.activeClients.remove(str);
            }
        }
        notifyListeners();
    }

    int getActiveClientCount() {
        return this.activeClients.size();
    }

    Collection<PushClientsManager.ClientLoginInfo> getAllClientLoginInfoByChid(String str) {
        if (this.activeClients.containsKey(str)) {
            return ((HashMap) this.activeClients.get(str).clone()).values();
        }
        return new ArrayList();
    }

    ArrayList<PushClientsManager.ClientLoginInfo> getAllClients() {
        ArrayList<PushClientsManager.ClientLoginInfo> arrayList = new ArrayList<>();
        Iterator<HashMap<String, PushClientsManager.ClientLoginInfo>> it = this.activeClients.values().iterator();
        while (it.hasNext()) {
            arrayList.addAll(it.next().values());
        }
        return arrayList;
    }

    PushClientsManager.ClientLoginInfo getClientLoginInfoByChidAndUserId(String str, String str2) {
        HashMap<String, PushClientsManager.ClientLoginInfo> map = this.activeClients.get(str);
        if (map == null) {
            return null;
        }
        return map.get(PushClientsManager.getSmtpLocalPart(str2));
    }

    Iterable<HashMap<String, PushClientsManager.ClientLoginInfo>> getActiveClientMaps() {
        return this.activeClients.values();
    }

    void removeActiveClients() {
        Iterator<PushClientsManager.ClientLoginInfo> it = getAllClients().iterator();
        while (it.hasNext()) {
            it.next().unwatch();
        }
        this.activeClients.clear();
    }

    void removeAllClientChangeListeners() {
        this.clientChangeListeners.clear();
    }

    void removeClientChangeListener(PushClientsManager.ClientChangeListener clientChangeListener) {
        this.clientChangeListeners.remove(clientChangeListener);
    }

    private void notifyListeners() {
        Iterator<PushClientsManager.ClientChangeListener> it = this.clientChangeListeners.iterator();
        while (it.hasNext()) {
            it.next().onChange();
        }
    }
}
