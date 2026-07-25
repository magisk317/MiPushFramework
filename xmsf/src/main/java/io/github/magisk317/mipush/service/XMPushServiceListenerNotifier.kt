package io.github.magisk317.mipush.service

import android.content.Intent
import io.github.magisk317.xposed.logging.MagiskOtel

open class XMPushServiceListenerNotifier : XMPushServiceListener {
    private val listeners = ArrayList<XMPushServiceListener>()

    fun addListener(listener: XMPushServiceListener) {
        listeners.add(listener)
    }

    override fun created() {
        for (listener in listeners) {
            listener.created()
        }
        emit(stage = "created", reason = "fanout", listenerCount = listeners.size)
    }

    override fun destroy() {
        for (listener in listeners) {
            listener.destroy()
        }
        emit(stage = "destroy", reason = "fanout", listenerCount = listeners.size)
    }

    override fun start(intent: Intent) {
        for (listener in listeners) {
            listener.start(intent)
        }
        emit(
            stage = "start",
            reason = intent.action?.take(64) ?: "no_action",
            listenerCount = listeners.size,
        )
    }

    override fun connectionStatusChanged(connectionStatus: ConnectionStatus) {
        for (listener in listeners) {
            listener.connectionStatusChanged(connectionStatus)
        }
        emit(
            stage = "connection_status",
            reason = connectionStatus.name.lowercase(),
            listenerCount = listeners.size,
        )
    }

    private fun emit(stage: String, reason: String, listenerCount: Int) {
        MagiskOtel.event(
            name = "push.lifecycle",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "main",
                "stage" to stage,
                "reason" to reason,
                "pending_count" to listenerCount.toString(),
            ),
            statusOk = true,
        )
    }
}
