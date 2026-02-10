package com.magisk317.service

import android.content.Intent
import com.elvishew.xlog.XLog
import com.magisk317.network.NetworkPolicyCompat
import com.xiaomi.push.service.XMPushService
import java.util.ArrayDeque

/**
 * Runtime replacement for old AOP lifecycle callbacks around XMPushService.
 */
object XMPushServiceLifecycleBridge {
    private val logger = XLog.tag("XMPushServiceLifecycle").build()
    private val lock = Any()
    private var currentService: XMPushService? = null
    private var listener: XMPushServiceListener? = null
    private val pendingStarts = ArrayDeque<Intent>()

    @JvmStatic
    fun recordPendingStart(intent: Intent) {
        val immediateListener = synchronized(lock) {
            val current = listener
            if (current != null) {
                return@synchronized current
            }
            if (pendingStarts.size >= 16) {
                pendingStarts.removeFirst()
            }
            pendingStarts.addLast(Intent(intent))
            null
        }
        if (immediateListener != null) {
            runCatching { immediateListener.start(Intent(intent)) }
                .onFailure { logger.e("listener.start failed", it) }
        }
    }

    @JvmStatic
    fun ensureCreated(pushService: XMPushService) {
        var createdNow = false
        val activeListener = synchronized(lock) {
            if (currentService !== pushService || listener == null) {
                currentService = pushService
                listener = XMPushServiceAbility(pushService)
                createdNow = true
            }
            listener
        }
        if (createdNow) {
            NetworkPolicyCompat.installCountryCodeUrlRewrite(pushService)
            runCatching { activeListener?.created() }
                .onFailure { logger.e("listener.created failed", it) }
            flushPendingStarts(activeListener)
        }
    }

    @JvmStatic
    fun onStart(pushService: XMPushService, intent: Intent?) {
        ensureCreated(pushService)
        if (intent == null) return
        val activeListener = synchronized(lock) { listener } ?: return
        runCatching { activeListener.start(intent) }
            .onFailure { logger.e("listener.start failed", it) }
    }

    @JvmStatic
    fun onDestroy(pushService: XMPushService?) {
        val oldListener = synchronized(lock) {
            if (pushService != null && currentService !== pushService) return
            val l = listener
            listener = null
            currentService = null
            pendingStarts.clear()
            l
        } ?: return
        runCatching { oldListener.destroy() }
            .onFailure { logger.e("listener.destroy failed", it) }
    }

    @JvmStatic
    fun canStartForegroundImmediately(): Boolean = synchronized(lock) { currentService != null }

    private fun flushPendingStarts(activeListener: XMPushServiceListener?) {
        if (activeListener == null) return
        val starts = synchronized(lock) {
            if (pendingStarts.isEmpty()) return
            ArrayList<Intent>(pendingStarts).also { pendingStarts.clear() }
        }
        starts.forEach { intent ->
            runCatching { activeListener.start(intent) }
                .onFailure { logger.e("flush start callback failed", it) }
        }
    }
}
