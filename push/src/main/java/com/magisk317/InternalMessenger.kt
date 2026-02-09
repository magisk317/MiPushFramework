package com.magisk317

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.localbroadcastmanager.content.LocalBroadcastManager


import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch

object InternalEventBus {
    private val _events = MutableSharedFlow<Intent>(extraBufferCapacity = 64)
    val events = _events.asSharedFlow()

    fun emit(intent: Intent) {
        _events.tryEmit(intent)
    }
}

open class InternalMessenger(private val context: Context) {
    private val listeners: ArrayList<MessageListener> = ArrayList()
    private var job: Job? = null
    private val filters: ArrayList<IntentFilter> = ArrayList()

    fun send(intent: Intent) {
        InternalEventBus.emit(intent)
    }

    fun register(intentFilter: IntentFilter) {
        filters.add(intentFilter)
        startListening()
    }

    private fun startListening() {
        if (job != null) return
        job = CoroutineScope(Dispatchers.Main).launch {
            InternalEventBus.events.collect { intent ->
                if (matchesFilter(intent)) {
                    onReceive(intent)
                }
            }
        }
    }

    private fun matchesFilter(intent: Intent): Boolean {
        return filters.any { it.match(context.contentResolver, intent, false, "InternalMessenger") >= 0 }
    }

    fun unregister() {
        job?.cancel()
        job = null
        filters.clear()
    }

    fun addListener(listener: MessageListener) {
        listeners.add(listener)
    }

    private fun onReceive(intent: Intent) {
        for (listener in listeners) {
            listener.onReceive(intent)
        }
    }
}
