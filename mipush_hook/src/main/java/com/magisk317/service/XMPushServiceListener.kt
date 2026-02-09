package com.magisk317.service

import android.content.Intent

interface XMPushServiceListener {
    fun created() {}
    fun destroy() {}
    fun start(intent: Intent) {}

    enum class ConnectionStatus {
        connecting,
        connected,
        disconnected;

        companion object {
            @JvmStatic
            fun of(i: Int): ConnectionStatus {
                return values()[i]
            }
        }
    }

    fun connectionStatusChanged(connectionStatus: ConnectionStatus) {}
}

typealias ConnectionStatus = XMPushServiceListener.ConnectionStatus
