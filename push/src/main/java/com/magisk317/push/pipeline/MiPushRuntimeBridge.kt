package com.magisk317.push.pipeline

import android.content.Context
import android.content.Intent
import com.elvishew.xlog.XLog
import com.magisk317.Global
import com.magisk317.XMPushUtils
import com.magisk317.compat.RegistrationStateStore
import com.magisk317.service.RegisterRecorder
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.push.utils.RegSecUtils
import com.xiaomi.xmsf.utils.ConvertUtils
import top.trumeet.mipush.provider.db.EventDb
import top.trumeet.mipush.provider.db.RegisteredApplicationDb
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipush.provider.event.type.TypeFactory

object MiPushRuntimeBridge {
    private val logger = XLog.tag("MiPushRuntimeBridge").build()
    private const val RECENT_RECORD_WINDOW_MS = 10_000L
    private const val RECENT_REGISTER_TOAST_WINDOW_MS = 5_000L
    private val recentRecords = LinkedHashMap<String, Long>()
    private val recentAppActions = LinkedHashMap<String, Long>()
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
        val isMockReplay = MockMessageRegistry.isMarked(container)
        if (!isMockReplay && !shouldRecord(container)) {
            logger.d("skip duplicate payload event source=$source pkg=${container.packageName} action=${container.action}")
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
            if (isMockReplay) {
                logger.i("skip event record for mock replay source=$source pkg=${container.packageName}")
            } else {
                recordEvent(context, container)
            }
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
        applyRegistrationStateFromContainer(container, application)
        EventDb.insertEvent(Event.ResultType.OK, eventType)
        if (eventType.type == Event.Type.Registration || eventType.type == Event.Type.RegistrationResult) {
            maybeShowRegisterToast(context, pkg, application)
        }
    }

    private fun applyRegistrationStateFromContainer(
        container: XmPushActionContainer,
        application: RegisteredApplication
    ) {
        val nextType = when (container.action) {
            ActionType.UnRegistration -> RegisteredApplication.RegisteredType.Unregistered
            ActionType.Registration -> resolveRegistrationState(container)
            else -> null
        } ?: return
        RegistrationStateStore.updateIfChanged(
            application = application,
            nextType = nextType,
            source = RegistrationStateStore.Source.SERVER_RESULT
        )
    }

    private fun resolveRegistrationState(container: XmPushActionContainer): Int? {
        if (container.isRequest) {
            return null
        }
        val result = runCatching {
            ConvertUtils.getResponseMessageBodyFromContainer(container, RegSecUtils.getRegSec(container))
                as? XmPushActionRegistrationResult
        }.getOrNull()
        return when {
            result == null -> null
            result.errorCode.toInt() == 0 -> RegisteredApplication.RegisteredType.Registered
            else -> RegisteredApplication.RegisteredType.Unregistered
        }
    }

    private fun shouldRecord(container: XmPushActionContainer): Boolean {
        val messageId = MessageIdentity.fromContainer(container)
        val pkg = container.packageName
        val action = container.action?.name ?: "unknown"
        val now = System.currentTimeMillis()
        
        synchronized(recordLock) {
            pruneExpiredRecordsLocked(now)
            
            // 1. Precise ID deduplication (Long window)
            if (messageId != null) {
                val lastIdTime = recentRecords[messageId]
                if (lastIdTime != null && (now - lastIdTime) <= RECENT_RECORD_WINDOW_MS) {
                    return false
                }
                recentRecords[messageId] = now
            }
            
            // 2. Burst deduplication for same App + Action (Short window)
            // Even if messageId is different (or null), we don't expect 10+ messages for same app in 2s
            if (pkg != null) {
                val appActionKey = "$pkg:$action"
                val lastAppActionTime = recentAppActions[appActionKey]
                if (lastAppActionTime != null && (now - lastAppActionTime) <= 2000L) {
                    return false
                }
                recentAppActions[appActionKey] = now
            }
            
            return true
        }
    }

    private fun pruneExpiredRecordsLocked(now: Long) {
        val recordIterator = recentRecords.entries.iterator()
        while (recordIterator.hasNext()) {
            val entry = recordIterator.next()
            if ((now - entry.value) > RECENT_RECORD_WINDOW_MS) {
                recordIterator.remove()
            }
        }
        
        val appActionIterator = recentAppActions.entries.iterator()
        while (appActionIterator.hasNext()) {
            val entry = appActionIterator.next()
            if ((now - entry.value) > 2000L) {
                appActionIterator.remove()
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
