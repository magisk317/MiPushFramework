package com.magisk317.push.pipeline

import android.content.Context
import android.content.Intent
import io.github.aakira.napier.Napier
import com.magisk317.Global
import com.magisk317.XMPushUtils
import com.magisk317.compat.RegistrationStateStore
import com.magisk317.service.RegisterRecorder
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmsf.runtime.PushRegistrationState
import com.xiaomi.xmsf.runtime.PushRuntime
import com.xiaomi.xmsf.push.utils.RegSecUtils
import com.xiaomi.xmsf.utils.ConvertUtils
import top.trumeet.mipush.provider.db.EventDb
import top.trumeet.mipush.provider.db.RegisteredApplicationDb
import top.trumeet.mipush.provider.entities.Event
import top.trumeet.mipush.provider.entities.RegisteredApplication
import top.trumeet.mipush.provider.event.type.TypeFactory
import kotlinx.coroutines.runBlocking

object MiPushRuntimeBridge {
    private val diagnosticPackages = setOf("com.ss.android.ugc.aweme")
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = "MiPushRuntimeBridge")
        fun i(msg: String) = Napier.i(msg, tag = "MiPushRuntimeBridge")
        fun e(msg: String, t: Throwable) = Napier.e(msg, t, tag = "MiPushRuntimeBridge")
    }
    private const val RECENT_REGISTER_TOAST_WINDOW_MS = 5_000L
    private val recentRegisterToasts = LinkedHashMap<String, Long>()
    private val registerToastLock = Any()

    @JvmStatic
    fun onApplicationIntentReceived(context: Context, intent: Intent?) {
        if (intent == null) return
        runCatching {
            Global.MiPushEventListener().receiveFromApplication(intent)
            RegisterRecorder(context).recordRegisterRequest(intent)
            intent.getStringExtra(top.trumeet.common.Constants.EXTRA_MI_PUSH_PACKAGE)
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
            Global.MiPushEventListener().transferToServer(intent)
        }.onFailure {
            logger.e("onIntentForwardedToServer failed", it)
        }
    }

    @JvmStatic
    fun onNotificationDispatch(context: Context, container: XmPushActionContainer?, payload: ByteArray?): Boolean {
        if (payload != null) {
            val shouldProcess = onPayloadFromServer(context, payload, payload.size.toLong(), "notification")
            if (!shouldProcess) {
                logger.d(
                    "skip duplicate notification dispatch pkg=${container?.packageName} action=${container?.action}"
                )
                return false
            }
        }
        if (container?.packageName in diagnosticPackages) {
            logger.i(
                "diagnostic notification dispatch pkg=${container?.packageName} action=${container?.action?.name} " +
                    "messageId=${MessageIdentity.fromContainer(container)} source=MiPushRuntimeBridge.onNotificationDispatch"
            )
        }
        PushRuntime.observeNotificationEvent(
            packageName = container?.packageName,
            action = "notify_push_message",
            source = "MiPushRuntimeBridge.onNotificationDispatch"
        )
        onTransferToApplication(container)
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
