package io.github.magisk317.mipush.service

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.network.NetworkPolicyCompat
import com.xiaomi.push.service.XMPushServiceCore
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Runtime replacement for old AOP lifecycle callbacks around XMPushServiceCore.
 */
object XMPushServiceLifecycleBridge {
    private val registry = XMPushServiceLifecycleRegistry()

    data class LifecycleSnapshot(
        val serviceReady: Boolean,
        val pendingStartCount: Int
    )

    @JvmStatic
    fun recordPendingStart(intent: Intent) {
        val immediateListener = registry.recordPendingStart(intent)
        if (immediateListener != null) {
            runCatching { immediateListener.start(Intent(intent)) }
                .onFailure { logE("listener.start failed", it) }
        }
    }

    @JvmStatic
    fun ensureCreated(pushService: XMPushServiceCore) {
        val startedAt = System.nanoTime()
        val attachResult = registry.attach(pushService) { service ->
            XMPushServiceAbility(service)
        }
        if (attachResult.createdNow) {
            com.xiaomi.push.service.PushHostManagerFactory.init(pushService)
            runCatching { attachResult.listener?.created() }
                .onFailure { logE("listener.created failed", it) }
            flushPendingStarts(attachResult.listener)
            emitLifecycle(startedAt, result = "ok", reason = "created")
        }
    }

    @JvmStatic
    fun onStart(pushService: XMPushServiceCore, intent: Intent?) {
        val startedAt = System.nanoTime()
        ensureCreated(pushService)
        if (intent == null) {
            emitLifecycle(startedAt, result = "skip", reason = "null_intent")
            return
        }
        val activeListener = registry.currentListener()
        if (activeListener == null) {
            emitLifecycle(startedAt, result = "skip", reason = "no_listener")
            return
        }
        runCatching { activeListener.start(intent) }
            .onSuccess { emitLifecycle(startedAt, result = "ok", reason = "start") }
            .onFailure {
                logE("listener.start failed", it)
                emitLifecycle(startedAt, result = "error", statusOk = false, reason = it.javaClass.simpleName)
            }
    }

    @JvmStatic
    fun onDestroy(pushService: XMPushServiceCore?) {
        val startedAt = System.nanoTime()
        val oldListener = registry.detach(pushService)
        if (oldListener == null) {
            emitLifecycle(startedAt, result = "skip", reason = "not_attached")
            return
        }
        runCatching { oldListener.destroy() }
            .onSuccess { emitLifecycle(startedAt, result = "ok", reason = "destroy") }
            .onFailure {
                logE("listener.destroy failed", it)
                emitLifecycle(startedAt, result = "error", statusOk = false, reason = it.javaClass.simpleName)
            }
    }

    private fun emitLifecycle(
        startedAt: Long,
        result: String,
        statusOk: Boolean = true,
        reason: String? = null,
    ) {
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        val attrs = mutableMapOf(
            "result" to result,
            "duration_ms" to durationMs.toString(),
            "process" to "main",
        )
        if (reason != null) attrs["reason"] = reason
        MagiskOtel.event(name = "push.lifecycle", attributes = attrs, statusOk = statusOk)
    }

    @JvmStatic
    fun canStartForegroundImmediately(): Boolean = registry.canStartForegroundImmediately()

    @JvmStatic
    fun onConnectionStatusChanged(connectionStatus: ConnectionStatus) {
        val activeListener = registry.currentListener()
        if (activeListener == null) {
            MagiskOtel.event(
                name = "push.lifecycle",
                attributes = mapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "main",
                    "stage" to "connection",
                    "reason" to "no_listener",
                    "network_available" to (connectionStatus.name != "disconnected").toString(),
                ),
                statusOk = true,
            )
            return
        }
        runCatching { activeListener.connectionStatusChanged(connectionStatus) }
            .onSuccess {
                MagiskOtel.event(
                    name = "push.lifecycle",
                    attributes = mapOf(
                        "result" to "ok",
                        "duration_ms" to "0",
                        "process" to "main",
                        "stage" to "connection",
                        "reason" to connectionStatus.name,
                        "network_available" to (connectionStatus.name != "disconnected").toString(),
                    ),
                    statusOk = true,
                )
            }
            .onFailure {
                logE("listener.connectionStatusChanged failed", it)
                MagiskOtel.event(
                    name = "push.lifecycle",
                    attributes = mapOf(
                        "result" to "error",
                        "duration_ms" to "0",
                        "process" to "main",
                        "stage" to "connection",
                        "reason" to it.javaClass.simpleName,
                    ),
                    statusOk = false,
                )
            }
    }

    @JvmStatic
    fun peekService(): XMPushServiceCore? = registry.withService { it }

    fun <T> withService(block: (XMPushServiceCore) -> T): T? = registry.withService(block)

    @JvmStatic
    fun snapshot(): LifecycleSnapshot {
        val snapshot = registry.snapshot()
        return LifecycleSnapshot(
            serviceReady = snapshot.serviceReady,
            pendingStartCount = snapshot.pendingStartCount
        )
    }

    private fun flushPendingStarts(activeListener: XMPushServiceListener?) {
        if (activeListener == null) return
        val starts = registry.drainPendingStarts()
        starts.forEach { intent ->
            runCatching { activeListener.start(intent) }
                .onFailure { logE("flush start callback failed", it) }
        }
    }
}
