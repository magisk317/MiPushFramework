package io.github.magisk317.mipush.service

import android.content.Intent
import com.xiaomi.push.service.XMPushService
import java.util.ArrayDeque

internal class XMPushServiceLifecycleRegistry {
    data class AttachResult(
        val listener: XMPushServiceListener?,
        val createdNow: Boolean
    )

    data class Snapshot(
        val serviceReady: Boolean,
        val pendingStartCount: Int
    )

    private val lock = Any()
    private var currentService: XMPushService? = null
    private var listener: XMPushServiceListener? = null
    private val pendingStarts = ArrayDeque<Intent>()

    fun recordPendingStart(intent: Intent): XMPushServiceListener? = synchronized(lock) {
        val current = listener
        if (current != null) {
            return current
        }
        if (pendingStarts.size >= MAX_PENDING_STARTS) {
            pendingStarts.removeFirst()
        }
        pendingStarts.addLast(Intent(intent))
        null
    }

    fun attach(pushService: XMPushService, factory: (XMPushService) -> XMPushServiceListener): AttachResult {
        return synchronized(lock) {
            if (currentService === pushService && listener != null) {
                return@synchronized AttachResult(listener = listener, createdNow = false)
            }
            val activeListener = factory(pushService)
            currentService = pushService
            listener = activeListener
            AttachResult(listener = activeListener, createdNow = true)
        }
    }

    fun currentListener(): XMPushServiceListener? = synchronized(lock) { listener }

    fun detach(pushService: XMPushService?): XMPushServiceListener? = synchronized(lock) {
        if (pushService != null && currentService !== pushService) {
            return null
        }
        val activeListener = listener
        listener = null
        currentService = null
        pendingStarts.clear()
        activeListener
    }

    fun canStartForegroundImmediately(): Boolean = synchronized(lock) { currentService != null }

    fun <T> withService(block: (XMPushService) -> T): T? {
        val service = synchronized(lock) { currentService } ?: return null
        return block(service)
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(
            serviceReady = currentService != null,
            pendingStartCount = pendingStarts.size
        )
    }

    fun drainPendingStarts(): List<Intent> = synchronized(lock) {
        if (pendingStarts.isEmpty()) {
            return emptyList()
        }
        ArrayList(pendingStarts).also { pendingStarts.clear() }
    }

    private companion object {
        const val MAX_PENDING_STARTS = 16
    }
}
