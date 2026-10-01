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

    /**
     * Outcome of a notification dispatch attempt.
     *
     * [allowanceState] is only set when the dispatch was denied for lack of a publish grant and
     * carries the reason the grant was absent, which is what makes duplicate-dispatch telemetry
     * attributable to a root cause instead of a bare `no_allowance`.
     */
    data class NotificationDispatchDecision(
        val allowed: Boolean,
        val denyReason: String? = null,
        val allowanceState: String? = null,
    )

    /**
     * Outcome of a dispatch-allowance consumption attempt. [Rejected.state] distinguishes the
     * three ways a grant can be absent:
     * - `missing`: no grant was ever issued in this process for this message; the typical cause
     *   is the inbound dedup window suppressing the payload that would have marked the grant.
     * - `expired`: a grant existed but aged past the TTL before the publish attempt landed.
     * - `exhausted`: every grant of the allowance budget was already consumed by earlier publish
     *   attempts of the same message (repeat delivery storm).
     */
    sealed class AllowanceDecision {
        object Granted : AllowanceDecision()
        data class Rejected(val state: String) : AllowanceDecision()
    }

    private val diagnosticPackages = setOf("com.ss.android.ugc.aweme")
    private const val NOTIFICATION_DISPATCH_ALLOWANCE_TTL_MS = 30_000L
    private const val NOTIFICATION_DISPATCH_ALLOWANCE_COUNT = 3
    private const val ALLOWANCE_STATE_MISSING = "missing"
    private const val ALLOWANCE_STATE_EXPIRED = "expired"
    private const val ALLOWANCE_STATE_EXHAUSTED = "exhausted"
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
            PushShellBridgeHolder.events().receiveFromApplication(intent)
            PushShellBridgeHolder.events().recordRegisterRequest(context, intent)
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
            PushShellBridgeHolder.events().transferToServer(intent)
        }.onFailure {
            logE("onIntentForwardedToServer failed", it)
        }
    }

    @JvmStatic
    fun onNotificationDispatch(
        context: Context,
        container: XmPushActionContainer?,
        payload: ByteArray?,
    ): NotificationDispatchDecision {
        val startedAt = System.nanoTime()
        fun finish(
            allowed: Boolean,
            reason: String,
            targetPackage: String = "",
            allowanceState: String? = null,
        ): NotificationDispatchDecision {
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
            if (!allowanceState.isNullOrBlank()) {
                attrs["allowance_state"] = allowanceState
            }
            MagiskOtel.event(name = "push.dispatch", attributes = attrs, statusOk = true)
            return NotificationDispatchDecision(
                allowed = allowed,
                denyReason = if (allowed) null else reason,
                allowanceState = allowanceState,
            )
        }

        val resolvedContainer = container ?: payload?.let { PushShellBridgeHolder.payload().packToContainer(it) }
        val actionName = resolvedContainer?.action?.name ?: "Unknown"
        val messageId = MessageIdentity.fromContainer(resolvedContainer)
        val isMockReplay = MockMessageRegistry.isMarked(resolvedContainer)
        val userId = Utils.requireValidUserId(Utils.myUserId())
        if (resolvedContainer != null &&
            PushShellBridgeHolder.policy().shouldDropNotification(
                context,
                resolvedContainer,
                "MiPushRuntimeBridge.onNotificationDispatch"
            )
        ) {
            logI(
                "drop notification dispatch for absent package pkg=${PushShellBridgeHolder.payload().resolveTargetPackage(resolvedContainer)} " +
                    "action=$actionName messageId=$messageId"
            )
            return finish(
                allowed = false,
                reason = "stale_package",
                targetPackage = PushShellBridgeHolder.payload().resolveTargetPackage(resolvedContainer).orEmpty(),
            )
        }
        if (payload != null && resolvedContainer != null && !isMockReplay) {
            when (val decision = consumeNotificationDispatchAllowance(
                packageName = resolvedContainer.packageName,
                actionName = actionName,
                messageId = messageId,
                userId = userId,
            )) {
                is AllowanceDecision.Granted -> Unit
                is AllowanceDecision.Rejected -> {
                    logD(
                        "skip notification dispatch without allowance pkg=${resolvedContainer.packageName} " +
                            "action=$actionName messageId=$messageId allowanceState=${decision.state} " +
                            "source=MiPushRuntimeBridge.onNotificationDispatch"
                    )
                    return finish(
                        allowed = false,
                        reason = "no_allowance",
                        targetPackage = resolvedContainer.packageName.orEmpty(),
                        allowanceState = decision.state,
                    )
                }
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
        val container = PushShellBridgeHolder.payload().packToContainer(payload) ?: return false
        if (MIPushEventProcessor.shouldCheckProfile(container) &&
            !PushShellBridgeHolder.payload().isProfileAllowed(context, container)
        ) {
            // Storage/allowance fence only. The decrypted MIPushEventProcessor gate owns the one
            // stock profileId_missing ACK and mismatch event; do not duplicate feedback here.
            logI(
                "fence payload outside registered profile source=$source pkg=${container.packageName} " +
                    "action=${container.action?.name} messageId=${MessageIdentity.fromContainer(container)}",
            )
            return false
        }
        if (PushShellBridgeHolder.policy().shouldDropInbound(context, container, source)) {
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
        val userId = Utils.requireValidUserId(Utils.myUserId())
        val shouldProcess = shouldProcessPayloadIdentity(
            packageName = container.packageName,
            actionName = actionName,
            messageId = messageId,
            source = source,
            isAck = container.action == ActionType.AckMessage,
            isMockReplay = isMockReplay,
            payloadSize = payload.size,
            androidUserId = userId,
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
            PushShellBridgeHolder.events().receiveFromServer(container)
        }.onFailure {
            logE("receiveFromServer callback failed source=$source", it)
        }
        runCatching {
            if (isMockReplay) {
                logD("skip event record for mock replay source=$source pkg=${container.packageName}")
            } else {
                PushShellBridgeHolder.events().recordEvent(context, container)
            }
        }.onFailure {
            logE("recordEvent failed source=$source packetBytesLen=$packetBytesLen", it)
        }
        return true
    }

    @JvmStatic
    fun onTransferToApplication(payload: ByteArray?) {
        val container = PushShellBridgeHolder.payload().packToContainer(payload) ?: return
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
            PushShellBridgeHolder.events().transferToApplication(container)
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
        androidUserId: Int,
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
            androidUserId = androidUserId,
        )
        if (!shouldProcess) {
            if (packageName in diagnosticPackages) {
                logI(
                    "diagnostic duplicate skip pkg=$packageName action=$actionName messageId=$messageId source=$source"
                )
            }
            logD("skip duplicate payload event source=$source pkg=$packageName action=$actionName")
            if (!isMockReplay) {
                // The inbound dedup window used to be invisible: the suppressed payload never
                // reached the publish path, so the resulting dispatch-allowance miss looked
                // unattributable downstream. Emit the suppression here (mock replays bypass the
                // dedup window by design and stay silent).
                val attrs = mutableMapOf(
                    "result" to "skip",
                    "duration_ms" to "0",
                    "process" to "xmsf",
                    "stage" to "inbound_dedup",
                    "reason" to "duplicate",
                    "action" to actionName,
                )
                if (!packageName.isNullOrBlank()) {
                    attrs["target_package"] = packageName
                }
                MagiskOtel.event(name = "push.receive", attributes = attrs, statusOk = true)
            }
        }
        return shouldProcess
    }

    /** Drops notification dispatch grants that were issued for a package whose data was cleared. */
    fun clearPackageTransientState(packageName: String, userId: Int) {
        if (packageName.isBlank()) return
        val prefix = "${Utils.requireValidUserId(userId)}|$packageName|"
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
            // Exhausted grants are kept until they age out so a later consume of the same
            // message can report `exhausted` instead of an unattributable `missing`.
            if ((nowMs - entry.value.updatedAtMs) > NOTIFICATION_DISPATCH_ALLOWANCE_TTL_MS) {
                iterator.remove()
            }
        }
    }

    @JvmStatic
    fun markNotificationDispatchAllowance(
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

    @JvmStatic
    fun consumeNotificationDispatchAllowance(
        packageName: String?,
        actionName: String,
        messageId: String?,
        userId: Int,
        nowMs: Long = System.currentTimeMillis()
    ): AllowanceDecision {
        val key = buildNotificationDispatchKey(packageName, actionName, messageId, userId)
            ?: return AllowanceDecision.Granted
        synchronized(notificationDispatchLock) {
            val allowance = notificationDispatchAllowances[key] ?: run {
                pruneNotificationDispatchAllowancesLocked(nowMs)
                return AllowanceDecision.Rejected(ALLOWANCE_STATE_MISSING)
            }
            if ((nowMs - allowance.updatedAtMs) > NOTIFICATION_DISPATCH_ALLOWANCE_TTL_MS) {
                notificationDispatchAllowances.remove(key)
                pruneNotificationDispatchAllowancesLocked(nowMs)
                return AllowanceDecision.Rejected(ALLOWANCE_STATE_EXPIRED)
            }
            if (allowance.remaining <= 0) {
                return AllowanceDecision.Rejected(ALLOWANCE_STATE_EXHAUSTED)
            }
            allowance.remaining -= 1
            allowance.updatedAtMs = nowMs
            logD(
                "consume notification dispatch allowance pkg=$packageName action=$actionName " +
                    "messageId=$messageId remaining=${allowance.remaining}"
            )
            return AllowanceDecision.Granted
        }
    }

    fun applyRegistrationStateFromContainer(
        context: Context,
        container: XmPushActionContainer,
        application: RuntimeRegisteredApplicationRow
    ) {
        val registrationOutcome = resolveRegistrationResultOutcome(container)
        if (registrationOutcome != null) {
            PushShellBridgeHolder.registration().forgetPendingRegistration(context, application.packageName)
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
        PushShellBridgeHolder.registration().updateRegistrationState(application, nextType)
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
                // Product deviation from stock 7.4.67-C (which drops a success result that
                // lacks regSecret): the service-side registration record can be lost while
                // the cloud and the app-side SDK keep a valid registration, and the cloud
                // answer to the resulting re-registration carries the registration id without
                // re-issuing appId/regSecret. Accepting errorCode==0 with an identity is what
                // lets those apps leave "not registered"; the dual-store persist below still
                // requires the full pair, and the getRegSecs recovery healer repairs the
                // decryption secret independently.
                if (result.errorCode == 0L && result.registrationConfirmationId() != null) {
                    ConfirmedRegistrationTransition(
                        registeredType = RegisteredAppRegisteredType.Registered,
                        appId = result.appId?.takeIf { it.isNotBlank() },
                        regSecret = result.regSecret?.takeIf { it.isNotBlank() },
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

    /**
     * Cloud registration results identify the confirmed registration via regId; the
     * re-registration responses observed in the field carry the value in the request-echo
     * slot only, so either non-blank field confirms the registration (stock keeps both set).
     */
    private fun XmPushActionRegistrationResult.registrationConfirmationId(): String? =
        regId?.takeIf { it.isNotBlank() } ?: id?.takeIf { it.isNotBlank() }

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
            success = result.errorCode == 0L && result.registrationConfirmationId() != null,
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
        PushShellBridgeHolder.registration().forgetPendingRegistration(context, packageName)
        if (outcome.success && outcome.appId != null) {
            PushShellBridgeHolder.registration().queuePendingAppAbsent(context, packageName, outcome.appId)
            PushShellBridgeHolder.registration().forgetRegisteredPackage(context, packageName)
        }
    }

    fun persistConfirmedRegistrationState(
        context: Context,
        packageName: String,
        transition: ConfirmedRegistrationTransition,
    ) {
        when (transition.registeredType) {
            RegisteredAppRegisteredType.Registered -> {
                // Dual-store invariant (Requirements 3.6/12.1/12.2, Property 5): the app-id
                // map and the regSecret store are written only as one atomic pair. A relaxed
                // id-only confirmation flips the registration row via updateRegistrationState
                // above but stores nothing here; the missing half is supplied later by a full
                // registration response or by the getRegSecs recovery healer.
                val appId = transition.appId ?: return
                val regSecret = transition.regSecret ?: return
                PushShellBridgeHolder.registration().rememberRegisteredPackage(context, packageName, appId)
                PushShellBridgeHolder.registration().setRegSec(context, packageName, regSecret)
            }
            RegisteredAppRegisteredType.Unregistered ->
                PushShellBridgeHolder.registration().forgetRegisteredPackage(context, packageName)
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
            // Empty payload cannot build a container; the receiver wake-up itself is the point.
            putExtra("mipush_payload", ByteArray(0))
            addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)
        }
        runCatching { context.sendBroadcast(wakeUpIntent) }
            .onFailure { logI("wake-up broadcast failed pkg=$packageName: ${it.message}") }
        PushShellBridgeHolder.payload().dispatchToApplication(context, packageName, ByteArray(0))
        
        // 2. Mock connectivity change (connectivity change often triggers re-reg)
        val connIntent = Intent("android.net.conn.CONNECTIVITY_CHANGE").apply {
            `package` = packageName
        }
        runCatching { context.sendBroadcast(connIntent) }

        // 3. Request registration in our own runtime (to clear windows and prepare)
        PushRuntime.forceTriggerRegistration(packageName, "MiPushRuntimeBridge.triggerRegistration")
    }
}
