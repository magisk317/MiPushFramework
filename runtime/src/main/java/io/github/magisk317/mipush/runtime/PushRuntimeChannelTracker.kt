package io.github.magisk317.mipush.runtime

import android.content.Context
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.PushClientsManager.ClientLoginInfo
import com.xiaomi.push.service.PushClientsManager.ClientStatus
import com.xiaomi.smack.Connection
import io.github.aakira.napier.Napier

object PushRuntimeChannelTracker {
    private val logger = object {
        fun d(message: String) = Napier.d(message, tag = "PushRuntimeChannels")
        fun e(message: String, throwable: Throwable) = Napier.e(message, throwable, tag = "PushRuntimeChannels")
    }
    private val lock = Any()
    private var attached = false
    private val clientChangeListener = PushClientsManager.ClientChangeListener {
        syncNow("PushClientsManager.ClientChangeListener")
    }
    private val statusField by lazy(LazyThreadSafetyMode.NONE) {
        ClientLoginInfo::class.java.getDeclaredField("status").apply { isAccessible = true }
    }

    @JvmStatic
    fun attach(context: Context) {
        synchronized(lock) {
            if (attached) return
            PushClientsManager.getInstance().addClientChangeListener(clientChangeListener)
            attached = true
        }
        syncNow("PushRuntimeChannelTracker.attach")
        logger.d("channel tracker attached pkg=${context.packageName}")
    }

    @JvmStatic
    fun syncNow(source: String) {
        runCatching {
            val service = XMPushServiceLifecycleBridge.withService { it }
            val connectionState = when {
                service?.isConnected == true -> PushConnectionState.Connected
                service?.isConnecting == true -> PushConnectionState.Connecting
                service != null -> PushConnectionState.Disconnected
                else -> PushConnectionState.Idle
            }
            val host = service?.currentConnection?.getHost()
            val nowMs = System.currentTimeMillis()
            val records = PushClientsManager.getInstance().getAllClients().map { client ->
                PushChannelRecord(
                    packageName = client.pkgName,
                    channelId = client.chid,
                    userId = client.userId,
                    session = client.session,
                    state = readClientState(client),
                    updatedAtMs = nowMs,
                    source = source
                )
            }
            PushRuntime.synchronizeChannels(
                connectionState = connectionState,
                host = host,
                channels = records,
                source = source,
                nowMs = nowMs
            )
        }.onFailure {
            logger.e("syncNow failed source=$source", it)
        }
    }

    @JvmStatic
    fun observeConnectionState(
        newStatus: Int,
        reason: Int,
        source: String,
        connection: Connection? = XMPushServiceLifecycleBridge.withService { it.currentConnection }
    ) {
        val state = when (newStatus) {
            0 -> PushConnectionState.Connecting
            1 -> PushConnectionState.Connected
            2 -> PushConnectionState.Disconnected
            else -> PushConnectionState.Idle
        }
        PushRuntime.observeConnectionState(
            state = state,
            source = source,
            host = connection?.getHost(),
            reason = reason.toString()
        )
        syncNow("$source:sync")
    }

    private fun readClientState(client: ClientLoginInfo): PushChannelState {
        val status = runCatching { statusField.get(client) as? ClientStatus }.getOrNull()
        return when (status) {
            ClientStatus.binded -> PushChannelState.Bound
            ClientStatus.binding -> PushChannelState.Binding
            ClientStatus.unbind, null -> PushChannelState.Unbound
        }
    }
}
