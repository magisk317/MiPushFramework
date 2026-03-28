package com.xiaomi.push.service;

import android.os.IBinder;
import android.os.Messenger;
import com.xiaomi.channel.commonutils.logger.MyLog;

final class PushClientPeerWatcher implements IBinder.DeathRecipient {
    final PushClientsManager.ClientLoginInfo info;
    final Messenger peer;

    PushClientPeerWatcher(PushClientsManager.ClientLoginInfo clientLoginInfo, Messenger messenger) {
        this.info = clientLoginInfo;
        this.peer = messenger;
    }

    @Override
    public void binderDied() {
        MyLog.i("peer died, chid = " + this.info.chid);
        this.info.onPeerDied(this.peer);
    }
}
