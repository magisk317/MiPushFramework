package com.nihility.service

import com.xiaomi.push.service.XMPushServiceMessenger

class MessengerAbility(
    private val messenger: XMPushServiceMessenger
) : XMPushServiceListener {
    override fun connectionStatusChanged(connectionStatus: XMPushServiceListener.ConnectionStatus) {
        messenger.notifyConnectionStatusChanged(connectionStatus.ordinal)
    }
}
