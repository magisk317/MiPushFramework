package com.magisk317.service

import com.xiaomi.push.service.XMPushServiceMessenger

class MessengerAbility(
    private val messenger: XMPushServiceMessenger
) : XMPushServiceListener {
    override fun connectionStatusChanged(connectionStatus: ConnectionStatus) {
        messenger.notifyConnectionStatusChanged(connectionStatus.ordinal)
    }
}
