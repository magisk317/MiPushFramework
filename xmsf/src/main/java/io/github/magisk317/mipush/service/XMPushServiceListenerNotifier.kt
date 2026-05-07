package io.github.magisk317.mipush.service

import android.content.Intent

open class XMPushServiceListenerNotifier : XMPushServiceListener {
    private val listeners = ArrayList<XMPushServiceListener>()

    fun addListener(listener: XMPushServiceListener) {
        listeners.add(listener)
    }

    override fun created() {
        for (listener in listeners) {
            listener.created()
        }
    }

    override fun destroy() {
        for (listener in listeners) {
            listener.destroy()
        }
    }

    override fun start(intent: Intent) {
        for (listener in listeners) {
            listener.start(intent)
        }
    }

    override fun connectionStatusChanged(connectionStatus: ConnectionStatus) {
        for (listener in listeners) {
            listener.connectionStatusChanged(connectionStatus)
        }
    }
}
