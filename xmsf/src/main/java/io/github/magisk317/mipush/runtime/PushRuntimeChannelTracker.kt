package io.github.magisk317.mipush.runtime

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import com.xiaomi.push.service.PushClientsManager
import com.xiaomi.push.service.PushClientsManager.ClientLoginInfo
import com.xiaomi.push.service.PushClientsManager.ClientStatus
import com.xiaomi.smack.Connection
import io.github.magisk317.xposed.logging.MagiskOtel

object PushRuntimeChannelTracker {
    private data class LiveSnapshot(
        val connectionState: PushConnectionState,
        val host: String?,
        val records: List<PushChannelRecord>,
    )

    private val lock = Any()
    private var attached = false
    private val clientChangeListener = PushClientsManager.ClientChangeListener {
        syncNow("PushClientsManager.ClientChangeListener")
    }
    private val statusField by lazy {
        ClientLoginInfo::class.java.getDeclaredField("status").apply { isAccessible = true }
    }

    @JvmStatic
    fun attach(context: Context) {
        val alreadyAttached = synchronized(lock) {
            if (attached) {
                true
            } else {
                PushClientsManager.getInstance().addClientChangeListener(clientChangeListener)
                attached = true
                false
            }
        }
        MagiskOtel.event(
            name = "push.lifecycle",
            attributes = mapOf(
                "result" to if (alreadyAttached) "skip" else "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "channel_tracker",
                "reason" to if (alreadyAttached) "already_attached" else "attached",
            ),
            statusOk = true,
        )
        if (!alreadyAttached) {
            syncNow("PushRuntimeChannelTracker.attach")
            logD("channel tracker attached pkg=${context.packageName}")
        }
    }

    @JvmStatic
    fun syncNow(source: String) {
        synchronize(source, onlyIfChanged = false)
    }

    @JvmStatic
    fun syncIfChanged(source: String): Boolean {
        return synchronize(source, onlyIfChanged = true)
    }

    private fun synchronize(source: String, onlyIfChanged: Boolean): Boolean {
        val startedAt = System.nanoTime()
        return runCatching {
            val liveSnapshot = captureLiveSnapshot(source)
            if (onlyIfChanged && !needsSynchronization(liveSnapshot)) {
                return@runCatching false
            }
            val nowMs = System.currentTimeMillis()
            PushRuntime.synchronizeChannels(
                connectionState = liveSnapshot.connectionState,
                host = liveSnapshot.host,
                channels = liveSnapshot.records,
                source = source,
                nowMs = nowMs
            )
            MagiskOtel.event(
                name = "push.lifecycle",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                    "process" to "xmsf",
                    "stage" to "channel_sync",
                    "reason" to liveSnapshot.connectionState.name.lowercase(),
                    "source" to source,
                    "found_count" to liveSnapshot.records.size.toString(),
                ),
                statusOk = true,
            )
            true
        }.getOrElse {
            logE("syncNow failed source=$source", it)
            MagiskOtel.event(
                name = "push.lifecycle",
                attributes = mapOf(
                    "result" to "error",
                    "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                    "process" to "xmsf",
                    "stage" to "channel_sync",
                    "reason" to "sync_failed",
                    "source" to source,
                    "error_class" to it.javaClass.simpleName,
                ),
                statusOk = false,
            )
            false
        }
    }

    private fun captureLiveSnapshot(source: String): LiveSnapshot {
        val service = XMPushServiceLifecycleBridge.withService { it }
        val connectionState = when {
            service?.isConnected == true -> PushConnectionState.Connected
            service?.isConnecting == true -> PushConnectionState.Connecting
            service != null -> PushConnectionState.Disconnected
            else -> PushConnectionState.Idle
        }
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
        return LiveSnapshot(
            connectionState = connectionState,
            host = service?.currentConnection?.host,
            records = records,
        )
    }

    private fun needsSynchronization(liveSnapshot: LiveSnapshot): Boolean {
        val cached = PushRuntime.connectionSnapshot()
        return cached.connectionState != liveSnapshot.connectionState.name ||
            cached.serverHost != liveSnapshot.host ||
            cached.trackedChannelCount != liveSnapshot.records.size ||
            cached.boundChannelCount != liveSnapshot.records.count { it.state == PushChannelState.Bound }
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
        MagiskOtel.event(
            name = "push.network",
            attributes = mapOf(
                "result" to "ok",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "connection_state",
                "reason" to state.name.lowercase(),
                "source" to source,
                "change_count" to reason.toString(),
            ),
            statusOk = true,
        )
        PushRuntime.observeConnectionState(
            state = state,
            source = source,
            host = connection?.host,
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
