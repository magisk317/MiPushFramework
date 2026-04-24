package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.os.IBinder
import android.os.Messenger
import com.xiaomi.channel.commonutils.logger.MyLog

class PushClientPeerWatcher(
    val info: PushClientsManager.ClientLoginInfo,
    val peer: Messenger,
) : IBinder.DeathRecipient {
    override fun binderDied() {
        MyLog.i("peer died, chid = ${info.chid}")
        info.onPeerDied(peer)
    }
}
