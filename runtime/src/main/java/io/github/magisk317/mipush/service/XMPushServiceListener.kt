package io.github.magisk317.mipush.service

import io.github.magisk317.mipush.protocol.model.ConnectionStatus
import android.content.Intent

interface XMPushServiceListener {
    fun created() {}
    fun destroy() {}
    fun start(intent: Intent) {}

    fun connectionStatusChanged(connectionStatus: ConnectionStatus) {}
}

typealias ConnectionStatus = io.github.magisk317.mipush.protocol.model.ConnectionStatus
