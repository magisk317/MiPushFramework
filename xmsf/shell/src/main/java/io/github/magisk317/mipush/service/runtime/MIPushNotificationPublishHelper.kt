package io.github.magisk317.mipush.service.runtime

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.os.SystemClock
import android.widget.Toast
import androidx.annotation.NonNull
import androidx.core.app.NotificationCompat
import io.github.magisk317.mipush.push.hook.ExplicitHookBridge
import io.github.magisk317.mipush.push.hook.HookTraceCompat
import io.github.magisk317.mipush.push.pipeline.MessageIdentity
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import io.github.magisk317.mipush.push.pipeline.StalePackagePushGuard
import io.github.magisk317.mipush.platform.support.Global
import io.github.magisk317.mipush.platform.support.XMPushUtils
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.android.MIUIUtils
import com.xiaomi.mipush.sdk.PushMessageProcessor
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmsf.R
import com.xiaomi.xmsf.stock.StockNotificationPresentationBridge
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.NotificationGroupHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.clientReport.ReportConstants
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.notification.NotificationController
import io.github.magisk317.mipush.notification.AndroidWGroupStrategy
import io.github.magisk317.mipush.notification.LiveUpdateDetector
import io.github.magisk317.mipush.notification.VoipNotificationHelper
import io.github.magisk317.mipush.notification.SweetNotificationCoordinator
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


class MIPushNotificationPublishHelper {
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
                val dispatched = dispatchNonDisplayPayloadToApplication(context, container, decryptedContent, messageId) ||
                    dispatchMessageArrivedIfNeeded(
                        context = context,
                        container = container,
                        decryptedContent = decryptedContent,
                        allowNonDisplayNotification = shouldHandoffNonDisplayNotification(container),
                    )
                logD("skip non-display notification publish action=${container.action} pkg=${container.packageName} handedOff=$dispatched")
                return finish(
                    if (dispatched) MockReplayOutcome.Dispatched else MockReplayOutcome.Failed,
                    if (dispatched) "non_display_dispatched" else "non_display_failed",
                    container.packageName.orEmpty(),
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

        private fun resolveSendMessage(
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
        ): Boolean {
            if (!shouldDispatchMessageArrived(container, dispatchRequested, allowNonDisplayNotification)) return false

            if (!allowNonDisplayNotification) {
                val metaInfo = container.metaInfo
                if (!MIPushNotificationHelper.isNotifyForeground(metaInfo?.extra) &&
                    MIPushNotificationHelper.isApplicationForeground(context, container.packageName)
                ) {
                    // Stock 3.7.9 and 7.4.67-C keep MESSAGE_ARRIVED inside the same display branch as
                    // notify_foreground. When that branch is suppressed, normal app delivery owns it.
                    logD("skip message arrived for foreground target pkg=${container.packageName}")
                    return false
                }
            }

            val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
            // Keep the stock-compatible callback available even when the target process is idle.
            // The explicit package and receiver query constrain delivery without requiring XMSF to
            // observe a running target first; the callback is precisely what can wake its push
            // receiver and restore the target-owned notification lifecycle.
            if (targetPackage.isBlank()) {
                logD("skip message arrived because target package is blank")
                return false
            }
            val intent = Intent(PushConstants.MIPUSH_ACTION_MESSAGE_ARRIVED).apply {
                setPackage(targetPackage)
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, decryptedContent)
            }
            return try {
                if (context.packageManager.queryBroadcastReceivers(intent, 0).isNullOrEmpty()) {
                    logD("skip message arrived because receiver is absent pkg=$targetPackage")
                    false
                } else {
                    context.sendBroadcast(intent, MIPushHelper.getReceiverPermission(targetPackage))
                    PushRuntime.observeNotificationEvent(
                        packageName = targetPackage,
                        action = "message_arrived_broadcast",
                        source = "MIPushNotificationPublishHelper.dispatchMessageArrivedIfNeeded",
                    )
                    logI("message arrived broadcast sent pkg=$targetPackage")
                    true
                }
            } catch (t: Exception) {
                PushRuntime.observeNotificationEvent(
                    packageName = targetPackage,
                    action = "message_arrived_broadcast_failed",
                    source = "MIPushNotificationPublishHelper.dispatchMessageArrivedIfNeeded",
                )
                logE("message arrived broadcast failed pkg=$targetPackage", t)
                false
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
        ): Boolean {
            if (!shouldDispatchNonDisplayPayload(container)) {
                return false
            }
            val packageName = container.packageName
            if (packageName.isNullOrBlank()) {
                logW("skip non-display payload dispatch because package is blank action=${container.action}")
                return false
            }
            val action = container.action?.name ?: "Unknown"
            if (!claimNonDisplayDispatch(packageName, action, messageId)) {
                logD("skip duplicate non-display payload dispatch pkg=$packageName action=$action messageId=$messageId")
                return false
            }
            HookTraceCompat.notifyPushMessage(container, decryptedContent)
            val dispatched = XMPushUtils.dispatchToApplication(
                context = context,
                packageName = packageName,
                payload = decryptedContent,
                fromNotification = false
            )
            if (dispatched) {
                PushRuntime.observeTransferToApplication(
                    packageName = packageName,
                    action = action,
                    messageId = messageId,
                    source = "MIPushNotificationPublishHelper.nonDisplayPayload"
                )
            } else {
                logW("non-display payload dispatch failed pkg=$packageName action=$action messageId=$messageId")
            }
            return dispatched
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

        private fun doNotifyPushMessage(
            context: Context,
            container: XmPushActionContainer,
            decryptedContent: ByteArray,
            sendMessage: XmPushActionSendMessage?,
            dispatchMessageArrived: Boolean = false,
        ): MockReplayOutcome {
            val metaInfo = container.metaInfo
            val messageId = MessageIdentity.fromContainer(container)
            val isMockReplay = MockMessageRegistry.isMarked(container)
            if (metaInfo == null) {
                logW("doNotifyPushMessage: metaInfo is null, skip notification pkg=${container.packageName} messageId=$messageId")
                return finishNotificationDispatch(
                    context,
                    container,
                    decryptedContent,
                    MockReplayOutcome.Failed,
                    dispatchMessageArrived,
                )
            }
            val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
            val targetUserId = NotificationController.resolveNotificationUserId(context, targetPackage)
            val isMiui = MIUIUtils.isMIUI()
            if (shouldSuppressForegroundNotification(
                    extra = metaInfo.extra,
                    isMiui = isMiui,
                    isTargetForeground = isMiui &&
                        MIPushNotificationHelper.isApplicationForeground(context, container.packageName),
                )
            ) {
                // Stock 7.4.67-C i0 skips t0 entirely on MIUI when notify_foreground is not 1
                // and the target app is foreground. The old product path applied this condition
                // only to MESSAGE_ARRIVED, so it still posted a notification the server suppressed.
                logI("skip foreground notification pkg=$targetPackage messageId=$messageId")
                PushRuntime.observeNotificationEvent(
                    packageName = targetPackage,
                    action = "foreground_notification_suppressed",
                    source = "MIPushNotificationPublishHelper.doNotifyPushMessage",
                )
                return finishNotificationDispatch(
                    context,
                    container,
                    decryptedContent,
                    MockReplayOutcome.Dispatched,
                    dispatchMessageArrived,
                )
            }
            val notificationId = getNotificationId(container)
            logD(
                "doNotifyPushMessage pkg=$targetPackage messageId=$messageId " +
                    "notificationId=$notificationId mockReplay=$isMockReplay " +
                    "messageTs=${metaInfo.messageTs} notifyId=${metaInfo.notifyId}",
            )
            // Stock 7.4.67-C t0 invokes sweet reminder filtering before its VoIP sequence filter.
            // The retained 3.7.9 helper has no remind_status lifecycle, so keep the newer behavior
            // in the product dispatch path instead of modifying pinned SDK sources.
            if (SweetNotificationCoordinator.shouldSuppress(
                    context = context,
                    packageName = targetPackage,
                    notificationId = notificationId,
                    metaInfo = metaInfo,
                    userId = targetUserId,
                )
            ) {
                logD("skip repeated sweet notification pkg=$targetPackage messageId=$messageId")
                PushRuntime.observeNotificationEvent(
                    packageName = targetPackage,
                    action = "sweet_notification_drop",
                    source = "MIPushNotificationPublishHelper.doNotifyPushMessage",
                )
                return finishNotificationDispatch(
                    context,
                    container,
                    decryptedContent,
                    MockReplayOutcome.Failed,
                    dispatchMessageArrived,
                )
            }
            if (VoipNotificationHelper.shouldDropStale(metaInfo, targetPackage, targetUserId)) {
                logD("skip stale voip notification pkg=$targetPackage messageId=$messageId")
                PushRuntime.observeNotificationEvent(
                    packageName = targetPackage,
                    action = "voip_sequence_drop",
                    source = "MIPushNotificationPublishHelper.doNotifyPushMessage"
                )
                return finishNotificationDispatch(
                    context,
                    container,
                    decryptedContent,
                    MockReplayOutcome.Failed,
                    dispatchMessageArrived,
                )
            }
            if (VoipNotificationHelper.isVoipEndEvent(metaInfo)) {
                logD("cancel voip notification pkg=$targetPackage messageId=$messageId notificationId=$notificationId")
                NotificationController.cancel(context, container, notificationId, null, clearGroup = false)
                PushRuntime.observeNotificationEvent(
                    packageName = targetPackage,
                    action = "voip_cancel",
                    source = "MIPushNotificationPublishHelper.doNotifyPushMessage"
                )
                return finishNotificationDispatch(
                    context,
                    container,
                    decryptedContent,
                    MockReplayOutcome.Dispatched,
                    dispatchMessageArrived,
                )
            }
            // Stock 7.4.67-C i0/p0 intercepts an eligible hyper_type=1 notification after
            // duplicate/profile checks but before t0 builds the ordinary notification. The old
            // product path posted immediately, bypassing the target app's extension service.
            if (ExtensionNotificationCoordinator.handleIfEligible(
                    context = context,
                    container = container,
                    payload = decryptedContent,
                    publish = { resolvedContainer, resolvedPayload ->
                        val resolvedSendMessage = if (resolvedContainer === container) {
                            sendMessage
                        } else {
                            resolveSendMessage(context, resolvedContainer)
                        }
                        val outcome = postNotification(
                            context,
                            resolvedContainer,
                            resolvedPayload,
                            resolvedSendMessage,
                        )
                        // Stock 7.4.67-C returns from the original i0 branch, then invokes i0.f
                        // from ExtensionMessageHandleCenter after callback/fallback. The first
                        // implementation let the outer scope send MESSAGE_ARRIVED immediately
                        // with the old payload, including when the app suppressed notification.
                        finishNotificationDispatch(
                            context,
                            resolvedContainer,
                            resolvedPayload,
                            outcome,
                            dispatchMessageArrived,
                        )
                    },
                )
            ) {
                PushRuntime.observeNotificationEvent(
                    packageName = targetPackage,
                    action = "extension_notification_intercepted",
                    source = "MIPushNotificationPublishHelper.doNotifyPushMessage",
                )
                return MockReplayOutcome.Dispatched
            }
            // Stock 7.4.67-C t0 publishes without consulting com.xiaomi.push.sort; that filter is
            // owned by notification collection. The old pre-publish check turned collection state
            // into a user-visible delivery block, so publishing must remain independent here.
            return finishNotificationDispatch(
                context,
                container,
                decryptedContent,
                postNotification(context, container, decryptedContent, sendMessage),
                dispatchMessageArrived,
            )
        }

        private fun finishNotificationDispatch(
            context: Context,
            container: XmPushActionContainer,
            decryptedContent: ByteArray,
            outcome: MockReplayOutcome,
            dispatchMessageArrived: Boolean,
        ): MockReplayOutcome {
            if (dispatchMessageArrived) {
                dispatchMessageArrivedIfNeeded(context, container, decryptedContent)
            }
            return outcome
        }

        internal fun mapPublishResult(
            result: NotificationController.PublishResult,
        ): MockReplayOutcome = when (result) {
            NotificationController.PublishResult.Posted -> MockReplayOutcome.Posted
            NotificationController.PublishResult.ChannelDisabled -> MockReplayOutcome.FailedChannelDisabled
            NotificationController.PublishResult.SuppressedByPolicy -> MockReplayOutcome.Dispatched
            NotificationController.PublishResult.Failed -> MockReplayOutcome.Failed
        }

        private fun postNotification(
            context: Context,
            container: XmPushActionContainer,
            decryptedContent: ByteArray,
            sendMessage: XmPushActionSendMessage?,
        ): MockReplayOutcome {
            val metaInfo = container.metaInfo ?: return MockReplayOutcome.Failed
            val targetPackage = MIPushNotificationHelper.getTargetPackage(container)
            val messageId = MessageIdentity.fromContainer(container)
            val notificationId = getNotificationId(container)
            val result = MIPushNotificationPresentationSupport.getNotificationFor(
                context,
                container,
                decryptedContent,
                notificationId,
                sendMessage,
            )
            logD(
                "doNotifyPushMessage publish start pkg=$targetPackage action=${container.action} " +
                    "messageId=$messageId notificationId=${result.notificationId}"
            )
            return mapPublishResult(
                NotificationController.publish(
                    context,
                    metaInfo,
                    result.notificationId,
                    targetPackage,
                    result.notificationBuilder,
                ),
            )
        }


        @JvmStatic
        fun getNotificationId(container: XmPushActionContainer): Int {
            val packageName = MIPushNotificationHelper.getTargetPackage(container)
            val metaInfo = container.metaInfo
            val messageId = MessageIdentity.fromContainer(container)
            val isMockReplay = MockMessageRegistry.isMarked(container)
            if (isMockReplay && metaInfo != null) {
                val sourceId = metaInfo.extra?.get(MockMessageRegistry.EXTRA_MOCK_REPLAY_SOURCE_ID)
                    ?.takeIf { it.isNotBlank() }
                    ?: messageId?.takeIf { it.isNotBlank() }
                    ?: metaInfo.id?.takeIf { it.isNotBlank() }
                    ?: metaInfo.notifyId.toString()
                return "${packageName}_mock_replay:$sourceId".hashCode()
            }
            val notifyId = metaInfo?.notifyId ?: 0
            val result = (packageName.hashCode() / 10) * 10 + notifyId
            logD("getNotificationId pkg=$packageName notifyId=$notifyId mockReplay=$isMockReplay messageId=$messageId result=$result")
            return result
        }

        @JvmStatic
        fun getNotificationTag(packageName: String): String? = null

        @JvmStatic
        fun getNotificationTag(container: XmPushActionContainer): String? {
            return getNotificationTag(container.packageName)
        }

        internal fun resolveStockGroup(
            targetPackage: String,
            sourceGroup: String?,
            disableDefault: Boolean,
            isMiui: Boolean,
        ): String? = MIPushNotificationPolicy.resolveStockGroup(
            targetPackage = targetPackage,
            sourceGroup = sourceGroup,
            disableDefault = disableDefault,
            isMiui = isMiui,
        )

        internal fun shouldSkipForceGroup(sourceGroup: String?, strategy: Int): Boolean {
            return AndroidWGroupStrategy.shouldSkipForceGroup(sourceGroup, strategy)
        }

        internal fun buildStockMiuiIdentityExtras(
            container: XmPushActionContainer,
            targetPackage: String = MIPushNotificationHelper.getTargetPackage(container),
        ): Map<String, String> {
            val metaInfo = container.metaInfo
            val messageType = when {
                MIPushNotificationHelper.isNormalNotificationMessage(container) -> 1000
                MIPushNotificationHelper.isBusinessMessage(container) -> 3000
                else -> -1
            }
            return buildStockMiuiIdentityExtras(targetPackage, metaInfo?.id, messageType)
        }

        internal fun buildStockMiuiIdentityExtras(
            targetPackage: String,
            messageId: String?,
            eventMessageType: Int,
        ): Map<String, String> = MIPushNotificationPolicy.buildStockMiuiIdentityExtras(
            targetPackage = targetPackage,
            messageId = messageId,
            eventMessageType = eventMessageType,
        )

        private fun getSourceGroup(metaInfo: PushMetaInfo): String? {
            return XMPushUtils.getConfiguration(metaInfo)
                .notificationGroup(null)
                ?.takeIf { it.isNotBlank() }
        }

        @JvmStatic
        fun buildTargetIntentWithoutExtras(pkg: String, metaInfo: PushMetaInfo): Intent {
            val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE)
                .addCategory(metaInfo.notifyId.toString())
                .setClassName(pkg, CLASS_NAME_PUSH_MESSAGE_HANDLER)
            ExplicitHookBridge.onBuildIntent(intent, "MIPushNotificationPublishHelper.buildTargetIntentWithoutExtras")
            return intent
        }

        @JvmStatic
        fun getSdkIntent(context: Context, container: XmPushActionContainer): Intent? {
            return MIPushNotificationIntentSupport.getSdkIntent(context, container)
        }
    }
}
