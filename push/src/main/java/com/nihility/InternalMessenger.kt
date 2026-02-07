package com.nihility

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager

open class InternalMessenger(context: Context) : BroadcastReceiver() {
    private val localBroadcast: LocalBroadcastManager = LocalBroadcastManager.getInstance(context)
    private val listeners: ArrayList<MessageListener> = ArrayList()

    fun send(intent: Intent) {
        localBroadcast.sendBroadcast(intent)
    }

    fun register(intentFilter: IntentFilter) {
        localBroadcast.registerReceiver(this, intentFilter)
    }

    fun addListener(listener: MessageListener) {
        listeners.add(listener)
    }

    override fun onReceive(context: Context, intent: Intent) {
        for (listener in listeners) {
            listener.onReceive(intent)
        }
    }
}
