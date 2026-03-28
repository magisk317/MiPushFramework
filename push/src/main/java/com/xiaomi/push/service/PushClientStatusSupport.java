package com.xiaomi.push.service;

import android.os.Message;
import android.os.RemoteException;
import com.xiaomi.channel.commonutils.logger.MyLog;
import com.xiaomi.slim.Blob;
import java.util.Iterator;

final class PushClientStatusSupport {
    private PushClientStatusSupport() {
    }

    static boolean isSpecialError(PushClientsManager.ClientLoginInfo clientLoginInfo, int i, int i2, String str) {
        switch (i) {
            case 1:
                if (clientLoginInfo.status == PushClientsManager.ClientStatus.binded || !clientLoginInfo.getPushService().isConnected() || i2 == 21) {
                    return false;
                }
                return (i2 == 7 && "wait".equals(str)) ? false : true;
            case 2:
                return clientLoginInfo.getPushService().isConnected();
            case 3:
                return !"wait".equals(str);
            default:
                return false;
        }
    }

    static void notifyClientStatus(PushClientsManager.ClientLoginInfo clientLoginInfo, int i, int i2, String str, String str2) {
        PushClientsManager.ClientStatus clientStatus = clientLoginInfo.status;
        clientLoginInfo.notifiedStatus = clientStatus;
        if (i == 2) {
            clientLoginInfo.mClientEventDispatcher.notifyChannelClosed(clientLoginInfo.context, clientLoginInfo, i2);
            return;
        }
        if (i == 3) {
            clientLoginInfo.mClientEventDispatcher.notifyKickedByServer(clientLoginInfo.context, clientLoginInfo, str2, str);
            return;
        }
        if (i == 1) {
            boolean z = clientStatus == PushClientsManager.ClientStatus.binded;
            if (!z && "wait".equals(str2)) {
                clientLoginInfo.currentRetrys++;
            } else if (z) {
                clientLoginInfo.currentRetrys = 0;
                if (clientLoginInfo.peer != null) {
                    try {
                        clientLoginInfo.peer.send(Message.obtain(null, 16, clientLoginInfo.getPushService().getServiceMessenger()));
                    } catch (RemoteException e) {
                    }
                }
            }
            clientLoginInfo.mClientEventDispatcher.notifyChannelOpenResult(clientLoginInfo.getPushService(), clientLoginInfo, z, i2, str);
        }
    }

    static boolean shouldNotifyClient(PushClientsManager.ClientLoginInfo clientLoginInfo, int i, int i2, String str) {
        PushClientsManager.ClientStatus clientStatus = clientLoginInfo.notifiedStatus;
        if (clientStatus == null || !clientLoginInfo.hasPeerSupport) {
            return true;
        }
        if (clientStatus == clientLoginInfo.status) {
            MyLog.i(" status recovered, don't notify client:" + clientLoginInfo.chid);
            return false;
        }
        if (clientLoginInfo.peer == null || !clientLoginInfo.hasPeerSupport) {
            MyLog.i("peer died, ignore notify " + clientLoginInfo.chid);
            return false;
        }
        MyLog.i("Peer alive notify status to client:" + clientLoginInfo.chid);
        return true;
    }

    static String getDesc(int i) {
        switch (i) {
            case 1:
                return "OPEN";
            case 2:
                return Blob.CMD_CLOSE;
            case 3:
                return Blob.CMD_KICK;
            default:
                return "unknown";
        }
    }

    static void notifyStatusListeners(PushClientsManager.ClientLoginInfo clientLoginInfo, PushClientsManager.ClientStatus clientStatus, int i) {
        synchronized (clientLoginInfo.statusChangeListeners) {
            Iterator<PushClientsManager.ClientLoginInfo.ClientStatusListener> it = clientLoginInfo.statusChangeListeners.iterator();
            while (it.hasNext()) {
                it.next().onChange(clientLoginInfo.status, clientStatus, i);
            }
        }
    }

    static int computeNotifyDelay(PushClientsManager.ClientLoginInfo clientLoginInfo) {
        if (clientLoginInfo.notifiedStatus == null || !clientLoginInfo.hasPeerSupport) {
            return 0;
        }
        return clientLoginInfo.peer != null && clientLoginInfo.hasPeerSupport ? 1000 : 10100;
    }
}
