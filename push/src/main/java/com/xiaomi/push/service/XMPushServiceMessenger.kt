package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.text.TextUtils
import com.nihility.InternalMessenger
import com.nihility.service.ForegroundHelper
import com.xiaomi.smack.Connection

class XMPushServiceMessenger(
    private val xmPushService: XMPushService
) : InternalMessenger(xmPushService) {

    private var connectionStatus: Int = 0

    init {
        register(IntentFilter(IntentGetConnectionStatus))
        register(IntentFilter(PushConstants.ACTION_RESET_CONNECTION))
        register(IntentFilter(IntentStartForeground))
    }

    override fun onReceive(context: Context, intent: Intent) {
        handle(intent)
        notifyConnectionStatusChanged(connectionStatus)
    }

    fun notifyConnectionStatusChanged(connectionStatus: Int) {
        this.connectionStatus = connectionStatus
        send(setConnectionStatusIntent(getDesc(connectionStatus)))
    }

    private fun setConnectionStatusIntent(connectionStatus: String): Intent {
        val intent = Intent(IntentSetConnectionStatus)
        intent.putExtra("status", connectionStatus)
        val currentConnection: Connection? = xmPushService.currentConnection
        if (currentConnection != null) {
            intent.putExtra("host", currentConnection.host)
        }
        return intent
    }

    private fun handle(intent: Intent) {
        when {
            TextUtils.equals(intent.action, PushConstants.ACTION_RESET_CONNECTION) -> {
                resetConnection()
            }

            TextUtils.equals(intent.action, IntentStartForeground) -> {
                ForegroundHelper(xmPushService).startForeground()
            }
        }
    }

    private fun resetConnection() {
        xmPushService.executeJob(ResetConnectJob(xmPushService))
    }

    fun getDesc(status: Int): String {
        return when (status) {
            0 -> "connecting"
            1 -> "connected"
            2 -> "disconnected"
            else -> "unknown"
        }
    }

    companion object {
        const val IntentGetConnectionStatus = "getConnectionStatus"
        const val IntentSetConnectionStatus = "setConnectionStatus"
        const val IntentStartForeground = "startForeground"
    }
}
