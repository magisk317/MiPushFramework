package com.magisk317.push.pipeline

import android.content.Context
import android.content.Intent
import com.elvishew.xlog.XLog
import com.magisk317.Global
import com.magisk317.XMPushUtils
import com.nihility.service.RegisterRecorder
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import top.trumeet.mipush.provider.db.EventDb
import top.trumeet.mipush.provider.db.RegisteredApplicationDb
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.event.type.TypeFactory

object MiPushRuntimeBridge {
    private val logger = XLog.tag("MiPushRuntimeBridge").build()
    private const val RECENT_RECORD_WINDOW_MS = 10_000L
    private const val RECENT_REGISTER_TOAST_WINDOW_MS = 5_000L
    private val recentRecords = LinkedHashMap<String, Long>()
    private val recentRegisterToasts = LinkedHashMap<String, Long>()
    private val recordLock = Any()
    private val registerToastLock = Any()

    @JvmStatic
    fun onApplicationIntentReceived(context: Context, intent: Intent?) {
        if (intent == null) return
        runCatching {
            Global.MiPushEventListener().receiveFromApplication(intent)
            RegisterRecorder(context).recordRegisterRequest(intent)
        }.onFailure {
            logger.e("onApplicationIntentReceived failed", it)
        }
    }

    @JvmStatic
    fun onIntentForwardedToServer(intent: Intent?) {
        if (intent == null) return
        runCatching {
            Global.MiPushEventListener().transferToServer(intent)
        }.onFailure {
            logger.e("onIntentForwardedToServer failed", it)
        }
    }

    @JvmStatic
    fun onNotificationDispatch(context: Context, container: XmPushActionContainer?, payload: ByteArray?) {
        if (payload != null) {
            onPayloadFromServer(context, payload, payload.size.toLong(), "notification")
        }
        onTransferToApplication(container)
    }

    @JvmStatic
    fun onPayloadFromServer(
        context: Context,
        payload: ByteArray,
        packetBytesLen: Long,
        source: String
    ) {
        val container = XMPushUtils.packToContainer(payload) ?: return
        MockMessageRegistry.consumeIfMatched(container)
        if (!shouldRecord(container)) {
            logger.d("skip duplicate payload event source=$source")
            return
        }
        runCatching {
            Global.RegistrationRecorder().initContext(context.applicationContext)
            Global.RegistrationRecorder().recordRegSec(container)
        }.onFailure {
            logger.e("recordRegSec failed source=$source", it)
        }
        runCatching {
            Global.MiPushEventListener().receiveFromServer(container)
        }.onFailure {
            logger.e("receiveFromServer callback failed source=$source", it)
        }
        runCatching {
            recordEvent(context, container)
        }.onFailure {
            logger.e("recordEvent failed source=$source packetBytesLen=$packetBytesLen", it)
        }
    }

    @JvmStatic
    fun onTransferToApplication(payload: ByteArray?) {
        val container = XMPushUtils.packToContainer(payload) ?: return
        onTransferToApplication(container)
    }

    @JvmStatic
    fun onTransferToApplication(container: XmPushActionContainer?) {
        if (container == null) return
        runCatching {
            Global.MiPushEventListener().transferToApplication(container)
        }.onFailure {
            logger.e("transferToApplication callback failed", it)
        }
    }

    private fun recordEvent(context: Context, container: XmPushActionContainer) {
        val pkg = container.packageName
        if (pkg.isNullOrBlank()) {
            return
        }
        val eventType = TypeFactory.createForStore(container)
        val application = RegisteredApplicationDb.registerApplication(pkg)
        EventDb.insertEvent(Event.ResultType.OK, eventType)
        if (eventType.type == Event.Type.Registration || eventType.type == Event.Type.RegistrationResult) {
            maybeShowRegisterToast(context, pkg, application)
        }
    }

    private fun shouldRecord(container: XmPushActionContainer): Boolean {
        val messageId = MessageIdentity.fromContainer(container) ?: return true
        val now = System.currentTimeMillis()
        synchronized(recordLock) {
            pruneExpiredRecordsLocked(now)
            val previous = recentRecords[messageId]
            recentRecords[messageId] = now
            return previous == null || (now - previous) > RECENT_RECORD_WINDOW_MS
        }
    }

    private fun pruneExpiredRecordsLocked(now: Long) {
        val iterator = recentRecords.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((now - entry.value) > RECENT_RECORD_WINDOW_MS) {
                iterator.remove()
            }
        }
    }

    private fun maybeShowRegisterToast(
        context: Context,
        pkg: String,
        application: top.trumeet.mipush.provider.entities.RegisteredApplication
    ) {
        val now = System.currentTimeMillis()
        synchronized(registerToastLock) {
            val iterator = recentRegisterToasts.entries.iterator()
            while (iterator.hasNext()) {
                val entry = iterator.next()
                if ((now - entry.value) > RECENT_REGISTER_TOAST_WINDOW_MS) {
                    iterator.remove()
                }
            }
            val previous = recentRegisterToasts[pkg]
            if (previous != null && now - previous <= RECENT_REGISTER_TOAST_WINDOW_MS) {
                return
            }
            recentRegisterToasts[pkg] = now
        }
        RegisterRecorder(context.applicationContext).showRegisterToastIfUserAllow(application)
    }
}
