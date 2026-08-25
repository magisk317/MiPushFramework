package io.github.magisk317.mipush.push.pipeline

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.push.service.MIPushAppInfo
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.runtime.PushRegistrationState
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import io.github.magisk317.mipush.runtime.store.kmp.RuntimeRegisteredApplicationRow
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import io.github.magisk317.mipush.push.bridge.PushShellBridgeHolder
import java.util.LinkedHashMap
import io.github.magisk317.xposed.logging.MagiskOtel

object MiPushRuntimeBridge {
    data class ConfirmedRegistrationTransition(
        val registeredType: Int,
        val appId: String? = null,
        val regSecret: String? = null,
    )

    data class RegistrationResultOutcome(
        val success: Boolean,
        val appId: String? = null,
        val regSecret: String? = null,
    )

    private val diagnosticPackages = setOf("com.ss.android.ugc.aweme")
    private const val NOTIFICATION_DISPATCH_ALLOWANCE_TTL_MS = 30_000L
    private const val NOTIFICATION_DISPATCH_ALLOWANCE_COUNT = 3
    private data class NotificationDispatchAllowance(
        var remaining: Int,
        var updatedAtMs: Long
    )
    private val notificationDispatchAllowances = LinkedHashMap<String, NotificationDispatchAllowance>()
    private val notificationDispatchLock = Any()

    @JvmStatic
    fun onApplicationIntentReceived(context: Context, intent: Intent?) {
        if (intent == null) return
        runCatching {
            PushShellBridgeHolder.require().receiveFromApplication(intent)
            PushShellBridgeHolder.require().recordRegisterRequest(context, intent)
            intent.getStringExtra(io.github.magisk317.mipush.common.Constants.EXTRA_MI_PUSH_PACKAGE)
                ?.takeIf { it.isNotBlank() }
                ?.takeIf { Utils.isUserApplication(context.applicationContext, it) }
                ?.let { packageName ->
                    when (intent.action) {
                        PushConstants.MIPUSH_ACTION_REGISTER_APP -> PushRuntime.observeRegistrationRequest(
                            packageName = packageName,
                            source = "application_intent:${intent.action ?: "unknown"}"
                        )
                        PushConstants.MIPUSH_ACTION_UNREGISTER_APP -> PushRuntime.observeChannelEvent(
                            packageName = packageName,
                            action = "unregistration_requested",
                            source = "application_intent:${intent.action ?: "unknown"}"
                        )
                    }
                }
        }.onFailure {
            logE("onApplicationIntentReceived failed", it)
        }
    }

    @JvmStatic
    fun onIntentForwardedToServer(intent: Intent?) {
        if (intent == null) return
        runCatching {
            PushShellBridgeHolder.require().transferToServer(intent)
        }.onFailure {
            logE("onIntentForwardedToServer failed", it)
        }
    }

    @JvmStatic
    fun onNotificationDispatch(context: Context, container: XmPushActionContainer?, payload: ByteArray?): Boolean {
        val startedAt = System.nanoTime()
        fun finish(allowed: Boolean, reason: String, targetPackage: String = ""): Boolean {
            val attrs = mutableMapOf(
                "result" to if (allowed) "ok" else "skip",
                "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                "process" to "xmsf",
                "stage" to "notify_dispatch",
                "reason" to reason,
            )
            if (targetPackage.isNotBlank()) {
                attrs["target_package"] = targetPackage
            }
            MagiskOtel.event(name = "push.dispatch", attributes = attrs, statusOk = true)
            return allowed
        }

        val resolvedContainer = container ?: payload?.let { PushShellBridgeHolder.require().packToContainer(it) }
        val actionName = resolvedContainer?.action?.name ?: "Unknown"
        val messageId = MessageIdentity.fromContainer(resolvedContainer)
        val isMockReplay = MockMessageRegistry.isMarked(resolvedContainer)
        val userId = Utils.myUserId().coerceAtLeast(0)
        if (resolvedContainer != null &&
            PushShellBridgeHolder.require().shouldDropNotification(
                context,
                resolvedContainer,
                "MiPushRuntimeBridge.onNotificationDispatch"
            )
        ) {
            logI(
                "drop notification dispatch for absent package pkg=${PushShellBridgeHolder.require().resolveTargetPackage(resolvedContainer)} " +
                    "action=$actionName messageId=$messageId"
            )
            return finish(
                allowed = false,
                reason = "stale_package",
                targetPackage = PushShellBridgeHolder.require().resolveTargetPackage(resolvedContainer).orEmpty(),
            )
        }
        if (payload != null && resolvedContainer != null && !isMockReplay) {
            val allowed = consumeNotificationDispatchAllowance(
                packageName = resolvedContainer.packageName,
                actionName = actionName,
                messageId = messageId,
                userId = userId,
            )
            if (!allowed) {
                logD(
                    "skip notification dispatch without allowance pkg=${resolvedContainer.packageName} " +
                        "action=$actionName messageId=$messageId source=MiPushRuntimeBridge.onNotificationDispatch"
                )
                return finish(
                    allowed = false,
                    reason = "no_allowance",
                    targetPackage = resolvedContainer.packageName.orEmpty(),
                )
            }
        }
        if (resolvedContainer?.packageName in diagnosticPackages) {
            logI(
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
        return finish(
            allowed = true,
            reason = "allowed",
            targetPackage = resolvedContainer?.packageName.orEmpty(),
        )
    }


    @JvmStatic
    fun onPayloadFromServer(
        context: Context,
        payload: ByteArray,
        packetBytesLen: Long,
        source: String
    ): Boolean {
        val container = PushShellBridgeHolder.require().packToContainer(payload) ?: return false
        if (MIPushEventProcessor.shouldCheckProfile(container) &&
            !PushShellBridgeHolder.require().isProfileAllowed(context, container)
        ) {
            // Storage/allowance fence only. The decrypted MIPushEventProcessor gate owns the one
            // stock profileId_missing ACK and mismatch event; do not duplicate feedback here.
            logI(
                "fence payload outside registered profile source=$source pkg=${container.packageName} " +
                    "action=${container.action?.name} messageId=${MessageIdentity.fromContainer(container)}",
            )
            return false
        }
        if (PushShellBridgeHolder.require().shouldDropInbound(context, container, source)) {
            handleRegistrationResultForAbsentPackage(context, container)
            logD(
                "drop payload for absent package source=$source pkg=${container.packageName} " +
                    "action=${container.action?.name} messageId=${MessageIdentity.fromContainer(container)}"
            )
            return false
        }
        if (container.packageName != null && RegisteredApplicationDb.isBlocked(container.packageName)) {
            logD("skip blocked application payload source=$source pkg=${container.packageName}")
            return false
        }
        val isMockReplay = MockMessageRegistry.isMarked(container)
        val actionName = container.action?.name ?: "Unknown"
        val messageId = MessageIdentity.fromContainer(container)
        val userId = Utils.myUserId().coerceAtLeast(0)
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
        if (shouldApplyServerRegistrationState(isMockReplay)) {
            persistConfirmedRegistrationStateFromContainer(context, container)
            markNotificationDispatchAllowance(
                packageName = container.packageName,
                actionName = actionName,
                messageId = messageId,
                userId = userId,
            )
        }
        runCatching {
            PushShellBridgeHolder.require().receiveFromServer(container)
        }.onFailure {
            logE("receiveFromServer callback failed source=$source", it)
        }
        runCatching {
            if (isMockReplay) {
                logD("skip event record for mock replay source=$source pkg=${container.packageName}")
            } else {
                PushShellBridgeHolder.require().recordEvent(context, container)
            }
        }.onFailure {
            logE("recordEvent failed source=$source packetBytesLen=$packetBytesLen", it)
        }
        return true
    }

    @JvmStatic
    fun onTransferToApplication(payload: ByteArray?) {
        val container = PushShellBridgeHolder.require().packToContainer(payload) ?: return
        onTransferToApplication(container)
    }

    @JvmStatic
    fun onTransferToApplication(container: XmPushActionContainer?) {
        if (container == null) return
        if (container.packageName in diagnosticPackages) {
            logI(
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
            PushShellBridgeHolder.require().transferToApplication(container)
        }.onFailure {
            logE("transferToApplication callback failed", it)
        }
    }

    fun shouldProcessPayloadIdentity(
        packageName: String?,
        actionName: String,
        messageId: String?,
        source: String,
        isAck: Boolean,
        isMockReplay: Boolean,
        payloadSize: Int? = null,
    ): Boolean {
        if (packageName in diagnosticPackages) {
            logI(
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
                logI(
                    "diagnostic duplicate skip pkg=$packageName action=$actionName messageId=$messageId source=$source"
                )
            }
            logD("skip duplicate payload event source=$source pkg=$packageName action=$actionName")
        }
        return shouldProcess
    }

    /** Drops notification dispatch grants that were issued for a package whose data was cleared. */
    fun clearPackageTransientState(packageName: String, userId: Int) {
        if (packageName.isBlank()) return
        val prefix = "${userId.coerceAtLeast(0)}|$packageName|"
        synchronized(notificationDispatchLock) {
            notificationDispatchAllowances.keys.removeIf { it.startsWith(prefix) }
        }
    }

    private fun buildNotificationDispatchKey(
        packageName: String?,
        actionName: String,
        messageId: String?,
        userId: Int,
    ): String? {
        if (packageName.isNullOrBlank() || messageId.isNullOrBlank()) {
            return null
        }
        return "$userId|$packageName|$actionName|$messageId"
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
        userId: Int,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val key = buildNotificationDispatchKey(packageName, actionName, messageId, userId) ?: return
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
        logD(
            "mark notification dispatch allowance pkg=$packageName action=$actionName " +
                "messageId=$messageId remaining=$NOTIFICATION_DISPATCH_ALLOWANCE_COUNT " +
                "ttlMs=$NOTIFICATION_DISPATCH_ALLOWANCE_TTL_MS"
        )
    }

    private fun consumeNotificationDispatchAllowance(
        packageName: String?,
        actionName: String,
        messageId: String?,
        userId: Int,
        nowMs: Long = System.currentTimeMillis()
    ): Boolean {
        val key = buildNotificationDispatchKey(packageName, actionName, messageId, userId) ?: return true
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
            logD(
                "consume notification dispatch allowance pkg=$packageName action=$actionName " +
                    "messageId=$messageId remaining=${allowance.remaining}"
            )
            if (allowance.remaining <= 0) {
                notificationDispatchAllowances.remove(key)
            }
            return accepted
        }
    }

    fun applyRegistrationStateFromContainer(
        context: Context,
        container: XmPushActionContainer,
        application: RuntimeRegisteredApplicationRow
    ) {
        val registrationOutcome = resolveRegistrationResultOutcome(container)
        if (registrationOutcome != null) {
            PushShellBridgeHolder.require().forgetPendingRegistration(context, application.packageName)
            if (!registrationOutcome.success) {
                PushRuntime.observeRegistrationResult(
                    packageName = application.packageName,
                    success = false,
                    source = "server_result:${container.action}",
                    reason = "registration_error",
                )
                return
            }
        }
        val transition = resolveConfirmedRegistrationTransition(container) ?: return
        val nextType = transition.registeredType
        PushShellBridgeHolder.require().updateRegistrationState(application, nextType)
        when (nextType) {
            RegisteredAppRegisteredType.Registered -> {
                MIPushAppInfo.getInstance(context).removeUnRegisteredPkg(application.packageName)
                PushRuntime.observeRegistrationResult(
                    packageName = application.packageName,
                    success = true,
                    source = "server_result:${container.action}"
                )
            }
            RegisteredAppRegisteredType.Unregistered -> {
                MIPushAppInfo.getInstance(context).addUnRegisteredPkg(application.packageName)
                PushRuntime.observeUnregistration(
                    packageName = application.packageName,
                    source = "server_result:${container.action}"
                )
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

    fun resolveServerRegistrationState(container: XmPushActionContainer): Int? {
        return resolveConfirmedRegistrationTransition(container)?.registeredType
    }

    fun resolveConfirmedRegistrationTransition(
        container: XmPushActionContainer,
    ): ConfirmedRegistrationTransition? {
        if (container.isRequest) {
            return null
        }
        val result = runCatching {
            decodeMessageBody(container)
        }.getOrNull() ?: return null
        return when (result) {
            is XmPushActionRegistrationResult -> {
                val appId = result.appId?.takeIf { it.isNotBlank() }
                val regSecret = result.regSecret?.takeIf { it.isNotBlank() }
                if (result.errorCode == 0L && appId != null && regSecret != null) {
                    ConfirmedRegistrationTransition(
                        registeredType = RegisteredAppRegisteredType.Registered,
                        appId = appId,
                        regSecret = regSecret,
                    )
                } else {
                    null
                }
            }
            is XmPushActionUnRegistrationResult -> {
                ConfirmedRegistrationTransition(RegisteredAppRegisteredType.Unregistered)
                    .takeIf { result.errorCode == 0L }
            }
            else -> null
        }
    }

    fun resolveRegistrationResultOutcome(
        container: XmPushActionContainer,
    ): RegistrationResultOutcome? {
        if (container.isRequest) return null
        val result = runCatching {
            decodeMessageBody(container)
        }.getOrNull() as? XmPushActionRegistrationResult ?: return null
        val appId = result.appId?.takeIf { it.isNotBlank() }
        val regSecret = result.regSecret?.takeIf { it.isNotBlank() }
        return RegistrationResultOutcome(
            success = result.errorCode == 0L && appId != null && regSecret != null,
            appId = appId,
            regSecret = regSecret,
        )
    }

    private fun decodeMessageBody(container: XmPushActionContainer): TBase<*, *>? {
        val bridge = PushShellBridgeHolder.peek()
        if (bridge != null) {
            return bridge.decodeMessageBody(container, bridge.getRegSec(container))
        }
        if (container.isEncryptAction) return null
        val response = when (container.action) {
            ActionType.Registration -> if (container.isRequest) {
                com.xiaomi.xmpush.thrift.XmPushActionRegistration()
            } else {
                XmPushActionRegistrationResult()
            }
            ActionType.UnRegistration -> if (container.isRequest) {
                com.xiaomi.xmpush.thrift.XmPushActionUnRegistration()
            } else {
                XmPushActionUnRegistrationResult()
            }
            else -> return null
        }
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(response, container.getPushAction())
        return response
    }

    private fun handleRegistrationResultForAbsentPackage(
        context: Context,
        container: XmPushActionContainer,
    ) {
        val packageName = container.packageName?.takeIf { it.isNotBlank() } ?: return
        val outcome = resolveRegistrationResultOutcome(container) ?: return
        PushShellBridgeHolder.require().forgetPendingRegistration(context, packageName)
        if (outcome.success && outcome.appId != null) {
            PushShellBridgeHolder.require().queuePendingAppAbsent(context, packageName, outcome.appId)
            PushShellBridgeHolder.require().forgetRegisteredPackage(context, packageName)
        }
    }

    fun persistConfirmedRegistrationState(
        context: Context,
        packageName: String,
        transition: ConfirmedRegistrationTransition,
    ) {
        when (transition.registeredType) {
            RegisteredAppRegisteredType.Registered -> {
                val appId = transition.appId ?: return
                val regSecret = transition.regSecret ?: return
                // Stock XMSF 7.4.67-C i0 accepts registration only when errorCode is zero and
                // regSecret is non-empty, then persists appId and secret as one confirmed result.
                // The old split recorder could confirm appId without a usable decryption secret.
                PushShellBridgeHolder.require().rememberRegisteredPackage(context, packageName, appId)
                PushShellBridgeHolder.require().setRegSec(context, packageName, regSecret)
            }
            RegisteredAppRegisteredType.Unregistered ->
                PushShellBridgeHolder.require().forgetRegisteredPackage(context, packageName)
        }
    }

    fun persistConfirmedRegistrationStateFromContainer(
        context: Context,
        container: XmPushActionContainer,
    ): Boolean {
        val packageName = container.packageName?.takeIf { it.isNotBlank() } ?: return false
        val transition = resolveConfirmedRegistrationTransition(container) ?: return false
        persistConfirmedRegistrationState(context, packageName, transition)
        return true
    }

    fun shouldApplyServerRegistrationState(isMockReplay: Boolean): Boolean = !isMockReplay

    @JvmStatic
    fun triggerRegistration(context: Context, packageName: String) {
        logI("force triggering registration for $packageName")
        
        // 1. Send wake-up intent (com.xiaomi.mipush.RECEIVE_MESSAGE)
        // Many MiPush SDK versions check registration status on any incoming message
        val wakeUpIntent = Intent("com.xiaomi.mipush.RECEIVE_MESSAGE").apply {
            `package` = packageName
            // Add a fake payload that will be ignored but triggers the receiver
            putExtra("mipush_payload", ByteArray(0))
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        PushShellBridgeHolder.require().dispatchToApplication(context, packageName, ByteArray(0))
        
        // 2. Mock connectivity change (connectivity change often triggers re-reg)
        val connIntent = Intent("android.net.conn.CONNECTIVITY_CHANGE").apply {
            `package` = packageName
        }
        runCatching { context.sendBroadcast(connIntent) }

        // 3. Request registration in our own runtime (to clear windows and prepare)
        PushRuntime.forceTriggerRegistration(packageName, "MiPushRuntimeBridge.triggerRegistration")
    }
}
