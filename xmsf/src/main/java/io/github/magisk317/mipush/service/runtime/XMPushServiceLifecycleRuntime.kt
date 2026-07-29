package io.github.magisk317.mipush.service.runtime

import com.xiaomi.push.service.PushClientsManager
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker

/**
 * Product-owned listener attached after the vendor service installs its stock client listener.
 *
 * Transport decisions stay in `XMPushServiceStockLifecycle`; this adapter only mirrors channel
 * state into MiPushFramework's product runtime and removes its own listener at service teardown.
 */
internal class XMPushServiceLifecycleRuntime(
    private val syncChannels: (String) -> Unit = PushRuntimeChannelTracker::syncNow,
) {
    private val lifecycleLock = Any()

    @Volatile
    private var closed = false
    private var clientsManager: PushClientsManager? = null
    private var clientChangeListener: PushClientsManager.ClientChangeListener? = null

    fun configureClientChangeListener(manager: PushClientsManager) {
        lateinit var listener: PushClientsManager.ClientChangeListener
        listener = PushClientsManager.ClientChangeListener {
            val isCurrent = synchronized(lifecycleLock) {
                !closed && clientsManager === manager && clientChangeListener === listener
            }
            if (isCurrent) {
                syncChannels(CLIENT_CHANGE_SYNC_SOURCE)
            }
        }
        val previous = synchronized(lifecycleLock) {
            if (closed) return
            val owned = clientsManager to clientChangeListener
            clientsManager = manager
            clientChangeListener = listener
            owned
        }
        val previousManager = previous.first
        val previousListener = previous.second
        if (previousManager != null && previousListener != null) {
            previousManager.removeClientChangeListener(previousListener)
        }
        manager.addClientChangeListener(listener)
        val isCurrent = synchronized(lifecycleLock) {
            !closed && clientsManager === manager && clientChangeListener === listener
        }
        if (isCurrent) {
            syncChannels(CLIENT_LISTENER_CONFIGURED_SYNC_SOURCE)
        } else {
            manager.removeClientChangeListener(listener)
        }
    }

    fun close() {
        val owned = synchronized(lifecycleLock) {
            if (closed) return
            closed = true
            val current = clientsManager to clientChangeListener
            clientsManager = null
            clientChangeListener = null
            current
        }
        val manager = owned.first
        val listener = owned.second
        if (manager != null && listener != null) {
            manager.removeClientChangeListener(listener)
        }
    }

    private companion object {
        const val CLIENT_CHANGE_SYNC_SOURCE =
            "XMPushServiceLifecycleRuntime.ClientChangeListener"
        const val CLIENT_LISTENER_CONFIGURED_SYNC_SOURCE =
            "XMPushServiceLifecycleRuntime.configureClientChangeListener"
    }
}
