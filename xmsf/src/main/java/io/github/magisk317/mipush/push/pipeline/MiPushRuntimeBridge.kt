package io.github.magisk317.mipush.push.pipeline

import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.compat.RegistrationStateStore
import io.github.magisk317.mipush.service.RegisterRecorder
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.runtime.PushRegistrationState
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.utils.ConvertUtils
import io.github.magisk317.mipush.runtime.store.db.EventDb
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.entities.Event
import io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication
import io.github.magisk317.mipush.runtime.store.event.type.TypeFactory
import kotlinx.coroutines.runBlocking
import java.util.LinkedHashMap

object MiPushRuntimeBridge {
    private val diagnosticPackages = setOf("com.ss.android.ugc.aweme")
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = "MiPushRuntimeBridge")
        fun i(msg: String) = Napier.i(msg, tag = "MiPushRuntimeBridge")
        fun e(msg: String, t: Throwable) = Napier.e(msg, t, tag = "MiPushRuntimeBridge")
    }
    private const val RECENT_REGISTER_TOAST_WINDOW_MS = 5_000L
    private const val NOTIFICATION_DISPATCH_ALLOWANCE_TTL_MS = 30_000L
    private val recentRegisterToasts = LinkedHashMap<String, Long>()
    private const val NOTIFICATION_DISPATCH_ALLOWANCE_COUNT = 3
    private data class NotificationDispatchAllowance(
        var remaining: Int,
        var updatedAtMs: Long
    )
    private val notificationDispatchAllowances = LinkedHashMap<String, NotificationDispatchAllowance>()
    private val registerToastLock = Any()
    private val notificationDispatchLock = Any()

    @JvmStatic
    fun onApplicationIntentReceived(context: Context, intent: Intent?) {
        if (intent == null) return
        runCatching {
            Global.miPushEventListener().receiveFromApplication(intent)
            RegisterRecorder(context).recordRegisterRequest(intent)
            intent.getStringExtra(io.github.magisk317.mipush.common.Constants.EXTRA_MI_PUSH_PACKAGE)
                ?.takeIf { it.isNotBlank() }
                ?.let { packageName ->
                    when (intent.action) {
                        PushConstants.MIPUSH_ACTION_REGISTER_APP -> PushRuntime.observeRegistrationRequest(
                            packageName = packageName,
                            source = "application_intent:${intent.action ?: "unknown"}"
                        )
                        PushConstants.MIPUSH_ACTION_UNREGISTER_APP -> PushRuntime.observeUnregistration(
                            packageName = packageName,
                            source = "application_intent:${intent.action ?: "unknown"}"
                        )
                    }
                }
        }.onFailure {
            logger.e("onApplicationIntentReceived failed", it)
        }
    }

    @JvmStatic
    fun onIntentForwardedToServer(intent: Intent?) {
        if (intent == null) return
        runCatching {
            Global.miPushEventListener().transferToServer(intent)
        }.onFailure {
            logger.e("onIntentForwardedToServer failed", it)
        }
    }

    @JvmStatic
    fun onNotificationDispatch(context: Context, container: XmPushActionContainer?, payload: ByteArray?): Boolean {
        val resolvedContainer = container ?: payload?.let(XMPushUtils::packToContainer)
        val actionName = resolvedContainer?.action?.name ?: "Unknown"
        val messageId = MessageIdentity.fromContainer(resolvedContainer)
        val isMockReplay = MockMessageRegistry.isMarked(resolvedContainer)
        if (payload != null && resolvedContainer != null && !isMockReplay) {
            val allowed = consumeNotificationDispatchAllowance(
                packageName = resolvedContainer.packageName,
                actionName = actionName,
                messageId = messageId
            )
            if (!allowed) {
                logger.d(
                    "skip notification dispatch without allowance pkg=${resolvedContainer.packageName} " +
                        "action=$actionName messageId=$messageId source=MiPushRuntimeBridge.onNotificationDispatch"
                )
                return false
            }
        }
        if (resolvedContainer?.packageName in diagnosticPackages) {
            logger.i(
                "diagnostic notification dispatch pkg=${resolvedContainer?.packageName} action=$actionName " +
                    "messageId=$messageId mockReplay=$isMockReplay source=MiPushRuntimeBridge.onNotificationDispatch"
            )
        }
        PushRuntime.observeNotificationEvent(
            packageName = resolvedContainer?.packageName,
            action = "notify_push_message",
            source = "MiPushRuntimeBridge.onNotificationDispatch"
        )
        onTransferToApplication(resolvedContainer)
        return true
    }

    @JvmStatic
    fun onPayloadFromServer(
        context: Context,
        payload: ByteArray,
        packetBytesLen: Long,
        source: String
    ): Boolean {
        val container = XMPushUtils.packToContainer(payload) ?: return false
        if (container.packageName != null && RegisteredApplicationDb.isBlocked(container.packageName)) {
            logger.d("skip blocked application payload source=$source pkg=${container.packageName}")
            return false
        }
        val isMockReplay = MockMessageRegistry.isMarked(container)
        val actionName = container.action?.name ?: "Unknown"
        val messageId = MessageIdentity.fromContainer(container)
        val shouldProcess = shouldProcessPayloadIdentity(
            packageName = container.packageName,
            actionName = actionName,
            messageId = messageId,
            source = source,
            isAck = container.action == ActionType.AckMessage,
            isMockReplay = isMockReplay,
            payloadSize = payload.size
        )
        if (!shouldProcess) {
            return false
        }
        if (!isMockReplay) {
            markNotificationDispatchAllowance(
                packageName = container.packageName,
                actionName = actionName,
                messageId = messageId
            )
        }
        runCatching {
            Global.registrationRecorder().initContext(context.applicationContext)
            Global.registrationRecorder().recordRegSec(container)
        }.onFailure {
            logger.e("recordRegSec failed source=$source", it)
        }
        runCatching {
            Global.miPushEventListener().receiveFromServer(container)
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
        return true
    }

    @JvmStatic
    fun onTransferToApplication(payload: ByteArray?) {
        val container = XMPushUtils.packToContainer(payload) ?: return
        onTransferToApplication(container)
    }

    @JvmStatic
    fun onTransferToApplication(container: XmPushActionContainer?) {
        if (container == null) return
        if (container.packageName in diagnosticPackages) {
            logger.i(
                "diagnostic transfer pkg=${container.packageName} action=${container.action?.name} " +
                    "messageId=${MessageIdentity.fromContainer(container)} source=MiPushRuntimeBridge.onTransferToApplication"
            )
        }
        PushRuntime.observeTransferToApplication(
            packageName = container.packageName,
            action = container.action?.name ?: "Unknown",
            messageId = MessageIdentity.fromContainer(container),
            source = "MiPushRuntimeBridge.onTransferToApplication"
        )
        runCatching {
            Global.miPushEventListener().transferToApplication(container)
        }.onFailure {
            logger.e("transferToApplication callback failed", it)
        }
    }

    private fun recordEvent(context: Context, container: XmPushActionContainer) {
        val pkg = container.packageName
        if (pkg.isNullOrBlank()) {
            return
        }
        if (RegisteredApplicationDb.isBlocked(pkg)) {
            logger.d("skip event record for blocked application pkg=$pkg")
            return
        }
        val eventType = TypeFactory.createForStore(container)
        val application = RegisteredApplicationDb.registerApplication(pkg)
        applyRegistrationStateFromContainer(container, application)
        runBlocking { EventDb.insertEventAsync(Event.ResultType.OK, eventType) }
        if (eventType.type == Event.Type.Registration || eventType.type == Event.Type.RegistrationResult) {
            maybeShowRegisterToast(context, pkg, application)
        }
    }

    internal fun shouldProcessPayloadIdentity(
        packageName: String?,
        actionName: String,
        messageId: String?,
        source: String,
        isAck: Boolean,
        isMockReplay: Boolean,
        payloadSize: Int? = null,
    ): Boolean {
        if (packageName in diagnosticPackages) {
            logger.i(
                "diagnostic inbound pkg=$packageName action=$actionName messageId=$messageId " +
                    "source=$source mockReplay=$isMockReplay payloadSize=${payloadSize ?: -1}"
            )
        }
        val shouldProcess = isMockReplay || PushRuntime.observeInboundMessage(
            packageName = packageName,
            action = actionName,
            messageId = messageId,
            source = source,
            isAck = isAck,
        )
        if (!shouldProcess) {
            if (packageName in diagnosticPackages) {
                logger.i(
                    "diagnostic duplicate skip pkg=$packageName action=$actionName messageId=$messageId source=$source"
                )
            }
            logger.d("skip duplicate payload event source=$source pkg=$packageName action=$actionName")
        }
        return shouldProcess
    }

    private fun buildNotificationDispatchKey(
        packageName: String?,
        actionName: String,
        messageId: String?
    ): String? {
        if (packageName.isNullOrBlank() || messageId.isNullOrBlank()) {
            return null
        }
        return "$packageName|$actionName|$messageId"
    }

    private fun pruneNotificationDispatchAllowancesLocked(nowMs: Long) {
        val iterator = notificationDispatchAllowances.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if ((nowMs - entry.value.updatedAtMs) > NOTIFICATION_DISPATCH_ALLOWANCE_TTL_MS || entry.value.remaining <= 0) {
                iterator.remove()
            }
        }
    }

    private fun markNotificationDispatchAllowance(
        packageName: String?,
        actionName: String,
        messageId: String?,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val key = buildNotificationDispatchKey(packageName, actionName, messageId) ?: return
        synchronized(notificationDispatchLock) {
            pruneNotificationDispatchAllowancesLocked(nowMs)
            val allowance = notificationDispatchAllowances[key]
            if (allowance == null) {
                notificationDispatchAllowances[key] = NotificationDispatchAllowance(
                    remaining = NOTIFICATION_DISPATCH_ALLOWANCE_COUNT,
                    updatedAtMs = nowMs
                )
            } else {
                allowance.remaining = maxOf(allowance.remaining, NOTIFICATION_DISPATCH_ALLOWANCE_COUNT)
                allowance.updatedAtMs = nowMs
            }
        }
        logger.d(
            "mark notification dispatch allowance pkg=$packageName action=$actionName " +
                "messageId=$messageId remaining=$NOTIFICATION_DISPATCH_ALLOWANCE_COUNT " +
                "ttlMs=$NOTIFICATION_DISPATCH_ALLOWANCE_TTL_MS"
        )
    }

    private fun consumeNotificationDispatchAllowance(
        packageName: String?,
        actionName: String,
        messageId: String?,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val key = buildNotificationDispatchKey(packageName, actionName, messageId) ?: return true
        synchronized(notificationDispatchLock) {
            pruneNotificationDispatchAllowancesLocked(nowMs)
            val allowance = notificationDispatchAllowances[key] ?: return false
            if ((nowMs - allowance.updatedAtMs) > NOTIFICATION_DISPATCH_ALLOWANCE_TTL_MS) {
                notificationDispatchAllowances.remove(key)
                return false
            }
            allowance.remaining -= 1
            allowance.updatedAtMs = nowMs
            val accepted = allowance.remaining >= 0
            logger.d(
                "consume notification dispatch allowance pkg=$packageName action=$actionName " +
                    "messageId=$messageId remaining=${allowance.remaining}"
            )
            if (allowance.remaining <= 0) {
                notificationDispatchAllowances.remove(key)
            }
            return accepted
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
        when (nextType) {
            RegisteredApplication.RegisteredType.Registered -> {
                PushRuntime.observeRegistrationResult(
                    packageName = application.packageName,
                    success = true,
                    source = "server_result:${container.action}"
                )
            }
            RegisteredApplication.RegisteredType.Unregistered -> {
                if (container.action == ActionType.UnRegistration) {
                    PushRuntime.observeUnregistration(
                        packageName = application.packageName,
                        source = "server_result:${container.action}"
                    )
                } else {
                    PushRuntime.observeRegistrationResult(
                        packageName = application.packageName,
                        success = false,
                        source = "server_result:${container.action}",
                        reason = "registration_error"
                    )
                }
            }
            else -> {
                PushRuntime.observeRegistrationState(
                    packageName = application.packageName,
                    state = PushRegistrationState.NotRegistered,
                    source = "server_result:${container.action}"
                )
            }
        }
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

    private fun maybeShowRegisterToast(
        context: Context,
        pkg: String,
        application: io.github.magisk317.mipush.runtime.store.entities.RegisteredApplication
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

    @JvmStatic
    fun triggerRegistration(context: Context, packageName: String) {
        logger.i("force triggering registration for $packageName")
        
        // 1. Send wake-up intent (com.xiaomi.mipush.RECEIVE_MESSAGE)
        // Many MiPush SDK versions check registration status on any incoming message
        val wakeUpIntent = Intent("com.xiaomi.mipush.RECEIVE_MESSAGE").apply {
            `package` = packageName
            // Add a fake payload that will be ignored but triggers the receiver
            putExtra("mipush_payload", ByteArray(0))
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        XMPushUtils.dispatchToApplication(context, packageName, ByteArray(0))
        
        // 2. Mock connectivity change (connectivity change often triggers re-reg)
        val connIntent = Intent("android.net.conn.CONNECTIVITY_CHANGE").apply {
            `package` = packageName
        }
        runCatching { context.sendBroadcast(connIntent) }

        // 3. Request registration in our own runtime (to clear windows and prepare)
        PushRuntime.forceTriggerRegistration(packageName, "MiPushRuntimeBridge.triggerRegistration")
    }
}
