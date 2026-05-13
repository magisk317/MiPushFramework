package io.github.magisk317.mipush.service

import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.network.NetworkPolicyCompat
import io.github.magisk317.mipush.service.runtime.RegistrationIntentDeduper
import com.xiaomi.push.service.XMPushService

/**
 * Runtime replacement for old AOP lifecycle callbacks around XMPushService.
 */
object XMPushServiceLifecycleBridge {
    private val logger = object {
        fun e(msg: String, t: Throwable) = Napier.e(msg, t, tag = "XMPushServiceLifecycle")
    }
    private val registry = XMPushServiceLifecycleRegistry()

    data class LifecycleSnapshot(
        val serviceReady: Boolean,
        val pendingStartCount: Int
    )

    @JvmStatic
    fun recordPendingStart(intent: Intent) {
        if (RegistrationIntentDeduper.shouldDrop("legacy_lifecycle", intent)) {
            return
        }
        val immediateListener = registry.recordPendingStart(intent)
        if (immediateListener != null) {
            runCatching { immediateListener.start(Intent(intent)) }
                .onFailure { logger.e("listener.start failed", it) }
        }
    }

    @JvmStatic
    fun ensureCreated(pushService: XMPushService) {
        val attachResult = registry.attach(pushService) { service ->
            XMPushServiceAbility(service)
        }
        if (attachResult.createdNow) {
            com.xiaomi.push.service.PushHostManagerFactory.init(pushService)
            runCatching { attachResult.listener?.created() }
                .onFailure { logger.e("listener.created failed", it) }
            flushPendingStarts(attachResult.listener)
        }
    }

    @JvmStatic
    fun onStart(pushService: XMPushService, intent: Intent?) {
        ensureCreated(pushService)
        if (intent == null) return
        val activeListener = registry.currentListener() ?: return
        runCatching { activeListener.start(intent) }
            .onFailure { logger.e("listener.start failed", it) }
    }

    @JvmStatic
    fun onDestroy(pushService: XMPushService?) {
        val oldListener = registry.detach(pushService) ?: return
        runCatching { oldListener.destroy() }
            .onFailure { logger.e("listener.destroy failed", it) }
    }

    @JvmStatic
    fun canStartForegroundImmediately(): Boolean = registry.canStartForegroundImmediately()

    @JvmStatic
    fun onConnectionStatusChanged(connectionStatus: ConnectionStatus) {
        val activeListener = registry.currentListener() ?: return
        runCatching { activeListener.connectionStatusChanged(connectionStatus) }
            .onFailure { logger.e("listener.connectionStatusChanged failed", it) }
    }

    @JvmStatic
    fun peekService(): XMPushService? = registry.withService { it }

    fun <T> withService(block: (XMPushService) -> T): T? = registry.withService(block)

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
                .onFailure { logger.e("flush start callback failed", it) }
        }
    }
}
