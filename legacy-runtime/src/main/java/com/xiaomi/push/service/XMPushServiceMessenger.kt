package com.xiaomi.push.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Handler
import android.os.Looper
import android.os.Message
import android.text.TextUtils
import com.xiaomi.smack.Connection

class XMPushServiceMessenger(
    private val xmPushService: XMPushService
) : BroadcastReceiver() {

    private var connectionStatus: Int = 0
    private val observer get() = xmPushService.runtimeObserver
    private val handler = object : Handler(Looper.getMainLooper()) {
        override fun handleMessage(msg: Message) {
            // Handle internal messages if needed
        }
    }

    init {
        val filter = IntentFilter().apply {
            addAction(IntentGetConnectionStatus)
            addAction(PushConstants.ACTION_RESET_CONNECTION)
            addAction(IntentStartForeground)
        }
        if (android.os.Build.VERSION.SDK_INT >= 34) {
            xmPushService.registerReceiver(this, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            xmPushService.registerReceiver(this, filter)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        handle(intent)
        notifyConnectionStatusChanged(connectionStatus)
    }

    fun notifyConnectionStatusChanged(connectionStatus: Int) {
        this.connectionStatus = connectionStatus

        // Delegate to observer instead of direct compat calls
        observer.onConnectionStateChanged(
            stateName = getDesc(connectionStatus),
            reason = "status_change",
            host = xmPushService.currentConnection?.host ?: "",
            message = ""
        )

        observer.sendBroadcast(setConnectionStatusIntent(getDesc(connectionStatus)))
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
                observer.startForegroundService()
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
