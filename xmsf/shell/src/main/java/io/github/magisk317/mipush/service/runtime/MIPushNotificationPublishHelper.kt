package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logW
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import io.github.magisk317.mipush.push.hook.HookTraceCompat
import io.github.magisk317.mipush.push.pipeline.MessageIdentity
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import io.github.magisk317.mipush.push.pipeline.StalePackagePushGuard
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.PushConstants
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.utils.Configurations
import io.github.magisk317.mipush.utils.ConvertUtils
import io.github.magisk317.mipush.utils.IconConfigurations
import io.github.magisk317.mipush.utils.RegSecUtils
import io.github.magisk317.mipush.app.ConfigCenter
import java.util.LinkedHashMap
import io.github.magisk317.mipush.common.Constants
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.common.utils.Utils
import io.github.magisk317.mipush.runtime.store.db.RegisteredApplicationDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExecutorCoroutineDispatcher
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.runBlocking
import java.util.concurrent.Executors
import io.github.magisk317.xposed.logging.MagiskOtel

/**
 * Publish-time facade for MiPush notifications: entry gating, policy dispatch,
 * configuration loading and the transient-state bookkeeping around a publish.
 *
 * Notification construction and the publish identity helpers (notification
 * id/tag, stock group and MIUI identity extras, target-intent building) live in
 * MIPushNotificationPublishConstruction.kt as same-package extension functions
 * on the companion, so call sites keep the
 * `MIPushNotificationPublishHelper.foo(...)` syntax.
 */
class MIPushNotificationPublishHelper {

    /**
     * Handoff outcome with a stable attribution cause. [cause] is null on success and uses
     * snake_case tokens that survive the telemetry reason sanitizer unchanged.
     */
    internal data class HandoffOutcome(
        val dispatched: Boolean,
        val cause: String? = null,
    )

    companion object {
        private const val TAG = "MyNotificationHelper"

        const val CLASS_NAME_PUSH_MESSAGE_HANDLER = Constants.PUSH_MESSAGE_HANDLER_CLASS
        private const val NOTIFICATION_GROUP_DISABLE_DEFAULT = "notification_group_disable_default"
        private const val NOTIFICATION_IS_SUMMARY = "notification_is_summary"
        private const val NOTIFICATION_MESSAGE_ID = "message_id"
        private const val NON_DISPLAY_DISPATCH_WINDOW_MS = 30_000L
        private const val CONFIGURATION_RETRY_DELAY_MS = 30_000L
        private const val NOTIFICATION_QUEUE_CAPACITY = 16

        /** A sender that sets the `wake` operation cannot hold a wake lock more than once per window. */
        private const val NOTIFICATION_WAKE_MIN_INTERVAL_MS = 5_000L
        private const val NOTIFICATION_WAKE_MAX_ENTRIES = 128

        @Volatile
        private var notificationSessionStartedAtMs: Long = System.currentTimeMillis()
        private val configurationLoadGate = ConfigurationLoadGate(CONFIGURATION_RETRY_DELAY_MS)
        private val notificationExecutor = Executors.newFixedThreadPool(3)
        private val notificationCoroutineDispatcher: ExecutorCoroutineDispatcher =
            notificationExecutor.asCoroutineDispatcher()
        private val notificationScope: CoroutineScope =
            CoroutineScope(SupervisorJob() + notificationCoroutineDispatcher)
        private val notificationDispatcher = KeyedSerialDispatcher<NotificationDispatchKey>(
            delegate = notificationExecutor,
            maxQueuedCommands = NOTIFICATION_QUEUE_CAPACITY,
            failureHandler = { key, error ->
                logE("Notification task failed key=$key", error)
            },
        )
        private val nonDisplayDispatchLock = Any()
        private val recentNonDisplayDispatches = LinkedHashMap<String, Long>()
        private val wakeScreenThrottle = WakeScreenThrottle(
            clock = { SystemClock.elapsedRealtime() },
            minimumIntervalMillis = NOTIFICATION_WAKE_MIN_INTERVAL_MS,
            maxEntries = NOTIFICATION_WAKE_MAX_ENTRIES,
        )
        @JvmStatic
        fun markNotificationSessionStarted(source: String, nowMs: Long = System.currentTimeMillis()) {
            notificationSessionStartedAtMs = nowMs
            logD("notification session started at=$nowMs source=$source")
        }

        @JvmStatic
        fun clearPackageTransientState(
            packageName: String,
            userId: Int = Utils.requireValidUserId(Utils.myUserId()),
        ) {
            val prefix = "$userId|$packageName|"
            synchronized(nonDisplayDispatchLock) {
                recentNonDisplayDispatches.keys.removeIf { it.startsWith(prefix) }
            }
        }

        @JvmStatic
        fun notifyPushMessage(
            context: Context,
            decryptedContent: ByteArray,
            dispatchMessageArrived: Boolean = false,
        ): MockReplayOutcome {
            val startedAt = System.nanoTime()
            fun finish(
                outcome: MockReplayOutcome,
                reason: String,
                targetPackage: String = "",
                resultOverride: String? = null,
                extraAttrs: Map<String, String> = emptyMap(),
            ): MockReplayOutcome {
                val result = resultOverride ?: when (outcome) {
                    MockReplayOutcome.Dispatched, MockReplayOutcome.Posted -> "ok"
                    MockReplayOutcome.BlockedByPermission -> "skip"
                    else -> "error"
                }
                val attrs = mutableMapOf(
                    "result" to result,
                    "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                    "process" to "xmsf",
                    "stage" to "notify_push_message",
                    "reason" to reason,
                    "payload_size" to decryptedContent.size.toString(),
                )
                if (targetPackage.isNotBlank()) {
                    attrs["target_package"] = targetPackage
                }
                attrs.putAll(extraAttrs)
                MagiskOtel.event(
                    name = "push.dispatch",
                    attributes = attrs,
                    statusOk = result != "error",
                )
                return outcome
            }

            val container = XMPushUtils.packToContainer(decryptedContent)
                ?: return finish(MockReplayOutcome.FailedPayloadMissing, "container_missing")
            val sendMessage = resolveSendMessage(context, container)
            val messageId = MessageIdentity.fromContainer(container)
            val isMockReplay = MockMessageRegistry.isMarked(container)
            logD(
                "notifyPushMessage start pkg=${container.packageName} action=${container.action} " +
                    "messageId=$messageId payloadSize=${decryptedContent.size} mockReplay=$isMockReplay " +
                    "moduleEnhanced=${io.github.magisk317.mipush.notification.NotificationManagerEx.isHooked}"
            )
            if (StalePackagePushGuard.shouldDropNotification(context, container, "MIPushNotificationPublishHelper.notifyPushMessage")) {
                logI(
                    "skip absent package notification pkg=${container.packageName} action=${container.action} " +
                        "messageId=$messageId"
                )
                return finish(MockReplayOutcome.FailedAppNotInstalled, "stale_package", container.packageName.orEmpty())
            }
            if (!shouldPublishNotification(container)) {
                val handoff = dispatchNonDisplayPayloadToApplication(context, container, decryptedContent, messageId)
                    .takeIf { it.dispatched }
                    ?: dispatchMessageArrivedIfNeeded(
                        context = context,
                        container = container,
                        decryptedContent = decryptedContent,
                        allowNonDisplayNotification = shouldHandoffNonDisplayNotification(container),
                    )
                val dispatched = handoff.dispatched
                logD("skip non-display notification publish action=${container.action} pkg=${container.packageName} handedOff=$dispatched")
                return finish(
                    if (dispatched) MockReplayOutcome.Dispatched else MockReplayOutcome.Failed,
                    if (dispatched) "non_display_dispatched" else "non_display_failed",
                    container.packageName.orEmpty(),
                    extraAttrs = handoff.cause?.let { mapOf("handoff_cause" to it) } ?: emptyMap(),
                )
            }
            if (MIPushEventProcessor.shouldCheckProfile(container) &&
                !StockSurfaceSupport.isProfileAllowed(context, container)
            ) {
                // The normal decrypted runtime gate owns the stock error ACK and accounting. This
                // is only a no-feedback guard for direct/fallback notification entry points.
                logI(
                    "skip notification outside registered profile pkg=${container.packageName} " +
                        "action=${container.action} messageId=$messageId",
                )
                return finish(MockReplayOutcome.Failed, "profile_not_allowed", container.packageName.orEmpty())
            }
            if (RegisteredApplicationDb.isBlocked(container.packageName)) {
                logD("skip blocked application pkg=${container.packageName} action=${container.action}")
                return finish(MockReplayOutcome.BlockedByPermission, "app_blocked", container.packageName.orEmpty())
            }
            if (!isMockReplay && shouldDropReplayNotification(container)) {
                val messageTs = container.metaInfo?.messageTs ?: 0L
                val sessionStartedAtMs = notificationSessionStartedAtMs
                logI(
                    "skip replay notification publish pkg=${container.packageName} action=${container.action} " +
                        "messageId=$messageId messageTs=$messageTs " +
                        "sessionStartedAtMs=$sessionStartedAtMs"
                )
                PushRuntime.observeNotificationEvent(
                    packageName = container.packageName,
                    action = "replay_notification_drop",
                    source = "MIPushNotificationPublishHelper.notifyPushMessage"
                )
                return finish(MockReplayOutcome.Failed, "replay_drop", container.packageName.orEmpty())
            }
            HookTraceCompat.notifyPushMessage(container, decryptedContent)
            val dispatchDecision = MiPushRuntimeBridge.onNotificationDispatch(context, container, decryptedContent)
            if (!dispatchDecision.allowed) {
                logD(
                    "skip duplicate notification publish action=${container.action} pkg=${container.packageName} " +
                        "messageId=$messageId mockReplay=$isMockReplay " +
                        "allowanceState=${dispatchDecision.allowanceState}"
                )
                // A denied dispatch is the allowance mechanism suppressing a repeat publish, not a
                // failure: classify it as skip and carry the bridge-side root cause plus the action
                // so the event stays attributable. The outcome stays Failed for the caller contract.
                return finish(
                    MockReplayOutcome.Failed,
                    "duplicate_dispatch",
                    container.packageName.orEmpty(),
                    resultOverride = "skip",
                    extraAttrs = buildDuplicateDispatchAttributes(
                        denyReason = dispatchDecision.allowanceState,
                        actionName = container.action?.name,
                    ),
                )
            }
            val notificationOp = AppInfoUtils.getAppNotificationOp(
                context,
                MIPushNotificationHelper.getTargetPackage(container),
                true
            )
            if (notificationOp == AppInfoUtils.AppNotificationOp.NOT_ALLOWED) {
                logW(
                    "Do not notify because user block " +
                        MIPushNotificationHelper.getTargetPackage(container) +
                        "'s notification messageId=$messageId mockReplay=$isMockReplay"
                )
                // Stock 3.7.9 and 7.4.67-C still send MESSAGE_ARRIVED after their notification
                // helper returns an empty result for a blocked post. The callback lets a running
                // target such as Weather render its own native notification surface.
                if (dispatchMessageArrived && !isMockReplay) {
                    dispatchMessageArrivedIfNeeded(context, container, decryptedContent)
                }
                return finish(MockReplayOutcome.BlockedByPermission, "notification_blocked", container.packageName.orEmpty())
            }
            loadConfigurationsOnce(context)
            val outcome = handleNotificationByConfigurations(
                context = context,
                decryptedContent = decryptedContent,
                packageName = container.packageName,
                container = container,
                sendMessage = sendMessage,
                dispatchMessageArrived = dispatchMessageArrived && !isMockReplay,
            )
            if (shouldFallbackToRawEncryptedDispatch(container.isEncryptAction, outcome)) {
                val packageName = MIPushNotificationHelper.getTargetPackage(container)
                val dispatched = dispatchRawEncryptedPayload(context, container, decryptedContent)
                return finish(
                    if (dispatched) MockReplayOutcome.Dispatched else MockReplayOutcome.Failed,
                    if (dispatched) "encrypted_raw_fallback" else "encrypted_raw_fallback_failed",
                    packageName,
                )
            }
            return finish(outcome, "configured", container.packageName.orEmpty())
        }

        internal fun shouldFallbackToRawEncryptedDispatch(
            isEncrypted: Boolean,
            outcome: MockReplayOutcome,
        ): Boolean = isEncrypted && outcome == MockReplayOutcome.Failed

        /**
         * Extra telemetry attributes for a dispatch denied by the allowance mechanism.
         *
         * [denyReason] is the bridge-side state (`missing` / `expired` / `exhausted`); it is only
         * attached when known so older bridge builds degrade to action-only attribution.
         */
        internal fun buildDuplicateDispatchAttributes(
            denyReason: String?,
            actionName: String?,
        ): Map<String, String> = linkedMapOf<String, String>().apply {
            put("action", actionName?.takeIf { it.isNotBlank() } ?: "unknown")
            if (!denyReason.isNullOrBlank()) {
                put("deny_reason", denyReason)
            }
        }

        private fun dispatchRawEncryptedPayload(
            context: Context,
            container: XmPushActionContainer,
            decryptedContent: ByteArray,
        ): Boolean {
            val packageName = MIPushNotificationHelper.getTargetPackage(container)
            val dispatched = packageName.isNotBlank() && XMPushUtils.dispatchToApplication(
                context = context,
                packageName = packageName,
                payload = decryptedContent,
                fromNotification = true,
            )
            PushRuntime.observeNotificationEvent(
                packageName = packageName,
                action = if (dispatched) {
                    "encrypted_raw_payload_dispatched"
                } else {
                    "encrypted_raw_payload_failed"
                },
                source = "MIPushNotificationPublishHelper.encryptedFallback",
            )
            logW(
                "encrypted notification processing failed; raw payload fallback " +
                    "pkg=$packageName dispatched=$dispatched",
            )
            return dispatched
        }

        internal fun resolveSendMessage(
            context: Context,
            container: XmPushActionContainer,
        ): XmPushActionSendMessage? {
            if (container.action != ActionType.SendMessage) return null
            val regSec = RegSecUtils.getRegSec(container)
            return try {
                val body = ConvertUtils.getResponseMessageBodyFromContainer(container, regSec)
                val sendMessage = body as? XmPushActionSendMessage
                logD(
                    "resolve SendMessage pkg=${container.packageName} " +
                        "isEncrypt=${container.isEncryptAction} regSecAvailable=${regSec != null} " +
                        "resultType=${body?.javaClass?.simpleName ?: "null"} " +
                        "sendMessageAvailable=${sendMessage != null}"
                )
                sendMessage
            } catch (error: Exception) {
                logW(
                    "resolve SendMessage failed pkg=${container.packageName} " +
                        "isEncrypt=${container.isEncryptAction} regSecAvailable=${regSec != null} " +
                        "messageId=${MessageIdentity.fromContainer(container)}: " +
                        (error.message ?: error.javaClass.simpleName)
                )
                null
            }
        }

        private fun handleNotificationByConfigurations(
            context: Context,
            decryptedContent: ByteArray,
            packageName: String,
            container: XmPushActionContainer,
            sendMessage: XmPushActionSendMessage?,
            dispatchMessageArrived: Boolean,
        ): MockReplayOutcome {
            val messageId = MessageIdentity.fromContainer(container)
            val isMockReplay = MockMessageRegistry.isMarked(container)
            // Configuration evaluation is isolated from delivery: an unreadable or malformed
            // per-package configuration must not swallow a valid payload. Stock delivers the
            // message whenever the payload itself is valid, so a failure here degrades to
            // notify-only (no wake, no open) instead of aborting the whole dispatch.
            val operations: Set<String>? = try {
                Configurations.getInstance().handle(packageName, container)
            } catch (error: Exception) {
                logW(
                    "configuration evaluation failed pkg=$packageName messageId=$messageId; " +
                        "falling back to notify-only",
                    error,
                )
                PushRuntime.observeNotificationEvent(
                    packageName = packageName,
                    action = "policy_evaluation_failed",
                    source = "MIPushNotificationPublishHelper.handleNotificationByConfigurations"
                )
                null
            }
            val plan = MIPushNotificationPolicy.resolveDispatchPlan(operations)
            logD(
                "handleNotificationByConfigurations pkg=$packageName action=${container.action} " +
                    "messageId=$messageId operations=$operations plan=$plan"
            )
            return try {
                if (plan.wake) {
                    PushRuntime.observeNotificationEvent(
                        packageName = packageName,
                        action = "policy_wake",
                        source = "MIPushNotificationPublishHelper.handleNotificationByConfigurations"
                    )
                    // Wake is an optional side effect: a wake-lock failure must not stop the
                    // notification from being posted.
                    try {
                        wakeScreen(context, packageName)
                    } catch (error: Exception) {
                        logW("wake screen failed pkg=$packageName messageId=$messageId", error)
                        PushRuntime.observeNotificationEvent(
                            packageName = packageName,
                            action = "policy_wake_failed",
                            source = "MIPushNotificationPublishHelper.handleNotificationByConfigurations"
                        )
                    }
                }
                val notificationOutcome = if (plan.notify) {
                    PushRuntime.observeNotificationEvent(
                        packageName = packageName,
                        action = "policy_notify",
                        source = "MIPushNotificationPublishHelper.handleNotificationByConfigurations"
                    )
                    if (isMockReplay) {
                        logD(
                            "policy_notify replay start pkg=$packageName action=${container.action} " +
                                "messageId=$messageId"
                        )
                        doNotifyPushMessage(context, container, decryptedContent, sendMessage)
                    } else {
                        notificationDispatcher.execute(notificationDispatchKeyFor(context, container)) {
                            val notificationOutcome = try {
                                logD(
                                    "policy_notify dispatch start pkg=$packageName action=${container.action} " +
                                        "messageId=$messageId"
                                )
                                doNotifyPushMessage(
                                    context = context,
                                    container = container,
                                    decryptedContent = decryptedContent,
                                    sendMessage = sendMessage,
                                    dispatchMessageArrived = dispatchMessageArrived,
                                )
                            } catch (e: Exception) {
                                logE(
                                    "policy_notify dispatch failed pkg=$packageName action=${container.action} " +
                                        "messageId=$messageId",
                                    e
                                )
                                MockReplayOutcome.Failed
                            }
                            if (shouldFallbackToRawEncryptedDispatch(container.isEncryptAction, notificationOutcome)) {
                                dispatchRawEncryptedPayload(context, container, decryptedContent)
                            }
                        }
                        MockReplayOutcome.Dispatched
                    }
                } else {
                    PushRuntime.observeNotificationEvent(
                        packageName = packageName,
                        action = "policy_ignore",
                        source = "MIPushNotificationPublishHelper.handleNotificationByConfigurations"
                    )
                    // PackageConfig ignore is product-only display policy. Stock 3.7.9 and
                    // 7.4.67-C do not use it to suppress the target app's arrival callback.
                    if (dispatchMessageArrived) {
                        dispatchMessageArrivedIfNeeded(context, container, decryptedContent)
                    }
                    MockReplayOutcome.BlockedByPermission
                }
                if (plan.open) {
                    PushRuntime.observeNotificationEvent(
                        packageName = packageName,
                        action = "policy_open",
                        source = "MIPushNotificationPublishHelper.handleNotificationByConfigurations"
                    )
                    notificationScope.launch {
                        try {
                            PushRuntime.dispatchDownstreamPayload(
                                packageName = packageName,
                                action = container.action?.name ?: "Unknown",
                                messageId = MessageIdentity.fromContainer(container),
                                payload = decryptedContent,
                                source = "MIPushNotificationPublishHelper.policy_open",
                                launchApp = true
                            )
                        } catch (e: Exception) {
                            logE("Failed to dispatch downstream payload", e)
                        }
                    }
                }
                notificationOutcome
            } catch (e: Exception) {
                logE("handleNotificationByConfigurations encountered error", e)
                MockReplayOutcome.Failed
            }
        }

        /** Build the same identity tuple used by the final notification publication path. */
        private fun notificationDispatchKeyFor(
            context: Context,
            container: XmPushActionContainer,
        ): NotificationDispatchKey {
            val packageName = MIPushNotificationHelper.getTargetPackage(container)
            return NotificationDispatchKey(
                userId = NotificationController.resolveNotificationUserId(context, packageName),
                packageName = packageName,
                notificationId = getNotificationId(container),
                tag = getNotificationTag(packageName),
            )
        }

        private fun loadConfigurationsOnce(context: Context) {
            val result = configurationLoadGate.ensureInitialized(
                nowMs = System.currentTimeMillis(),
                source = {
                    val configCenter: ConfigCenter = Global.configCenter()
                    runBlocking { configCenter.getConfigurationDirectoryAsync() }
                },
                initializer = { directory -> loadConfigurations(context, directory) },
            )
            if (result is ConfigurationLoadResult.Failed) {
                val error = result.error
                if (error == null) {
                    logW("Notification configuration initialization was incomplete; retry deferred")
                } else {
                    logE("Failed to load notification configurations; retry deferred", error)
                    Utils.makeText(context, error.toString(), Toast.LENGTH_LONG)
                }
            }
        }

        internal fun shouldPublishNotification(container: XmPushActionContainer): Boolean {
            val metaInfo = container.metaInfo
            return MIPushNotificationPolicy.shouldPublishNotification(
                action = container.action,
                passThrough = metaInfo?.passThrough,
                title = metaInfo?.title,
                description = metaInfo?.description,
            )
        }

        internal fun shouldDispatchMessageArrived(
            container: XmPushActionContainer,
            dispatchRequested: Boolean,
            allowNonDisplayNotification: Boolean = false,
        ): Boolean {
            // Stock fires the arrival callback from the display branch regardless of the
            // container action; the raw profile matcher only gates legacy SendMessage payloads.
            return dispatchRequested &&
                !MockMessageRegistry.isMarked(container) &&
                !MIPushNotificationHelper.isBusinessMessage(container) &&
                (allowNonDisplayNotification ||
                    container.action == ActionType.Notification ||
                    MIPushEventProcessor.shouldCheckProfile(container))
        }

        internal fun shouldSuppressForegroundNotification(
            extra: Map<String, String>?,
            isMiui: Boolean,
            isTargetForeground: Boolean,
        ): Boolean = MIPushNotificationPolicy.shouldSuppressForegroundNotification(
            isNotifyForeground = MIPushNotificationHelper.isNotifyForeground(extra),
            isMiui = isMiui,
            isTargetForeground = isTargetForeground,
        )

        internal fun dispatchMessageArrivedIfNeeded(
            context: Context,
            container: XmPushActionContainer,
            decryptedContent: ByteArray,
            dispatchRequested: Boolean = true,
            allowNonDisplayNotification: Boolean = false,
        ): HandoffOutcome {
            if (!shouldDispatchMessageArrived(container, dispatchRequested, allowNonDisplayNotification)) return HandoffOutcome(false, "not_requested")

            if (!allowNonDisplayNotification) {
                val metaInfo = container.metaInfo
                if (!MIPushNotificationHelper.isNotifyForeground(metaInfo?.extra) &&
                    MIPushNotificationHelper.isApplicationForeground(context, container.packageName)
                ) {
                    // Stock 3.7.9 and 7.4.67-C keep MESSAGE_ARRIVED inside the same display branch as
                    // notify_foreground. When that branch is suppressed, normal app delivery owns it.
                    logD("skip message arrived for foreground target pkg=${container.packageName}")
                    return HandoffOutcome(false, "foreground_suppressed")
                }
            }

            val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
            // Keep the stock-compatible callback available even when the target process is idle.
            // The explicit package and receiver query constrain delivery without requiring XMSF to
            // observe a running target first; the callback is precisely what can wake its push
            // receiver and restore the target-owned notification lifecycle.
            if (targetPackage.isBlank()) {
                logD("skip message arrived because target package is blank")
                return HandoffOutcome(false, "target_package_blank")
            }
            val intent = Intent(PushConstants.MIPUSH_ACTION_MESSAGE_ARRIVED).apply {
                setPackage(targetPackage)
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, decryptedContent)
            }
            return try {
                if (context.packageManager.queryBroadcastReceivers(intent, 0).isNullOrEmpty()) {
                    logD("skip message arrived because receiver is absent pkg=$targetPackage")
                    HandoffOutcome(false, "receiver_absent")
                } else {
                    context.sendBroadcast(intent, MIPushHelper.getReceiverPermission(targetPackage))
                    PushRuntime.observeNotificationEvent(
                        packageName = targetPackage,
                        action = "message_arrived_broadcast",
                        source = "MIPushNotificationPublishHelper.dispatchMessageArrivedIfNeeded",
                    )
                    logI("message arrived broadcast sent pkg=$targetPackage")
                    HandoffOutcome(true)
                }
            } catch (t: Exception) {
                PushRuntime.observeNotificationEvent(
                    packageName = targetPackage,
                    action = "message_arrived_broadcast_failed",
                    source = "MIPushNotificationPublishHelper.dispatchMessageArrivedIfNeeded",
                )
                logE("message arrived broadcast failed pkg=$targetPackage", t)
                HandoffOutcome(false, "send_failed")
            }
        }

        internal fun shouldDispatchNonDisplayPayload(container: XmPushActionContainer): Boolean =
            MIPushNotificationPolicy.shouldDispatchNonDisplayPayload(
                isRequest = container.isRequest,
                action = container.action,
            )

        internal fun shouldHandoffNonDisplayNotification(container: XmPushActionContainer): Boolean =
            MIPushNotificationPolicy.shouldHandoffNonDisplayNotification(
                action = container.action,
                isMockReplay = MockMessageRegistry.isMarked(container),
                isBusinessMessage = MIPushNotificationHelper.isBusinessMessage(container),
            )

        private fun dispatchNonDisplayPayloadToApplication(
            context: Context,
            container: XmPushActionContainer,
            decryptedContent: ByteArray,
            messageId: String?
        ): HandoffOutcome {
            if (!shouldDispatchNonDisplayPayload(container)) {
                return HandoffOutcome(false, "policy_not_handoff")
            }
            val packageName = container.packageName
            if (packageName.isNullOrBlank()) {
                logW("skip non-display payload dispatch because package is blank action=${container.action}")
                return HandoffOutcome(false, "package_blank")
            }
            val action = container.action?.name ?: "Unknown"
            if (!claimNonDisplayDispatch(packageName, action, messageId)) {
                logD("skip duplicate non-display payload dispatch pkg=$packageName action=$action messageId=$messageId")
                return HandoffOutcome(false, "duplicate_dispatch")
            }
            HookTraceCompat.notifyPushMessage(container, decryptedContent)
            val dispatchResult = XMPushUtils.dispatchToApplicationResult(
                context = context,
                packageName = packageName,
                payload = decryptedContent,
                fromNotification = false
            )
            if (dispatchResult.dispatched) {
                PushRuntime.observeTransferToApplication(
                    packageName = packageName,
                    action = action,
                    messageId = messageId,
                    source = "MIPushNotificationPublishHelper.nonDisplayPayload"
                )
                return HandoffOutcome(true)
            }
            logW("non-display payload dispatch failed pkg=$packageName action=$action messageId=$messageId")
            return HandoffOutcome(false, when (dispatchResult) {
                is XMPushUtils.DispatchResult.ServiceBlocked -> "service_blocked"
                else -> "app_dispatch_failed"
            })
        }

        private fun claimNonDisplayDispatch(
            packageName: String,
            action: String,
            messageId: String?,
            nowMs: Long = System.currentTimeMillis()
        ): Boolean {
            // Registration/UnRegistration/Command results carry no stock time-window dedup, and an
            // empty id would collapse distinct results into one key (userId|pkg|action|), wrongly
            // merging them inside the window. Dedup only when the container carries a real id.
            if (messageId.isNullOrBlank()) {
                return true
            }
            val key = "${Utils.requireValidUserId(Utils.myUserId())}|$packageName|$action|$messageId"
            synchronized(nonDisplayDispatchLock) {
                val iterator = recentNonDisplayDispatches.entries.iterator()
                while (iterator.hasNext()) {
                    val entry = iterator.next()
                    if ((nowMs - entry.value) > NON_DISPLAY_DISPATCH_WINDOW_MS) {
                        iterator.remove()
                    }
                }
                if (recentNonDisplayDispatches.containsKey(key)) {
                    return false
                }
                recentNonDisplayDispatches[key] = nowMs
                return true
            }
        }

        internal fun shouldDropReplayNotification(
            container: XmPushActionContainer,
            sessionStartedAtMs: Long = notificationSessionStartedAtMs,
        ): Boolean {
            val metaInfo = container.metaInfo
            return MIPushNotificationPolicy.shouldDropReplayNotification(
                action = container.action,
                hasMessageTimestamp = metaInfo?.isSetMessageTs() == true,
                messageTimestampMs = metaInfo?.messageTs ?: 0L,
                sessionStartedAtMs = sessionStartedAtMs,
            )
        }

        private fun loadConfigurations(context: Context, configurationDirectory: Uri?): Boolean {
            val configurations = Configurations.getInstance()
            val iconConfigurations: IconConfigurations = Global.iconConfigurations()
            val configurationsLoaded = configurations.init(context, configurationDirectory)
            val iconsLoaded = iconConfigurations.init(context, configurationDirectory)
            return configurationDirectory == null || configurationsLoaded && iconsLoaded
        }

        private fun wakeScreen(context: Context, sourcePackage: String) {
            if (!wakeScreenThrottle.tryAcquire(sourcePackage)) {
                logD("wake screen throttled source=$sourcePackage")
                return
            }
            val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            val fullWakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "xmsf: configurations of $sourcePackage"
            )
            fullWakeLock.acquire(10000)
        }

    }
}
