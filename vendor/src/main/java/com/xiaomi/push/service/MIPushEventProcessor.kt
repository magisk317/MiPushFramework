package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.text.TextUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.mipush.sdk.Constants
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.CommonPacketExtension
import com.xiaomi.smack.packet.Message
import com.xiaomi.smack.packet.Packet
import com.xiaomi.smack.util.TrafficUtils
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionCommand
import io.github.magisk317.xposed.logging.MagiskOtel

class MIPushEventProcessor {
    fun processChannelOpenResult(
        pushAction: IPushServiceAction,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
        success: Boolean,
        reasonCode: Int,
        reasonMessage: String?,
    ) {
        val account = pushAction.runtimeObserver.loadAccount(pushAction.context, "MIPushEventProcessor.processChannelOpenResult")
        if (success || account == null || reasonMessage != "token-expired") {
            return
        }
        pushAction.runtimeObserver.registerAccount(
            pushAction.context,
            account.packageName,
            account.appId,
            account.appToken,
            "MIPushEventProcessor.processChannelOpenResult",
        )
    }

    fun processNewPacket(
        pushAction: IPushServiceAction,
        blob: Blob,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
    ) {
        try {
            processMIPushMessage(pushAction, blob.getDecryptedPayload(clientLoginInfo.security), blob.serializedSize.toLong())
        } catch (e: IllegalArgumentException) {
            MyLog.e(e)
        }
    }

    fun processNewPacket(
        pushAction: IPushServiceAction,
        packet: Packet,
        clientLoginInfo: PushClientsManager.ClientLoginInfo,
    ) {
        if (packet !is Message) {
            MyLog.w("not a mipush message")
            return
        }
        val extension: CommonPacketExtension? = packet.getExtension("s")
        if (extension != null) {
            try {
                val decrypted = RC4Cryption.decrypt(
                    RC4Cryption.generateKeyForRC4(clientLoginInfo.security, packet.packetID ?: ""),
                    extension.text,
                )
                processMIPushMessage(pushAction, decrypted, TrafficUtils.getTrafficFlow(packet.toXML()).toLong())
            } catch (e: IllegalArgumentException) {
                MyLog.e(e)
            }
        }
    }

    companion object {
        // Stock 7.5.29 m0.g:715: the VoIP/CallKit pass-through marker on metaInfo.extra.
        private const val EXTRA_HYPER_TYPE = "hyper_type"
        private const val CALLKIT_HYPER_TYPE = "3"
        // Stock z0.a: msg-id ring key prefix for the callkit dedup (prefix + target pkg).
        private const val CALLKIT_MSG_ID_PREFIX = "CALLKIT_MSG_"
        // Stock u0.a/hc.a.c: "0" is the success result of the call-kit hand-off.
        private const val CALLKIT_RESULT_OK = "0"
        // Stock 7.5.29 dd.a:16-17: inbound LBS command names.
        private const val COMMAND_SUBSCRIBE_LBS_PUSH = "subscribe-lbs-push"
        private const val COMMAND_UNSUBSCRIBE_LBS_PUSH = "unsubscribe-lbs-push"

        @JvmStatic
        fun buildContainer(payload: ByteArray): XmPushActionContainer? {
            val container = XmPushActionContainer()
            return try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)
                container
            } catch (t: Throwable) {
                MyLog.e(t)
                null
            }
        }

        @JvmStatic
        fun buildIntent(payload: ByteArray, receivedAtMs: Long): Intent? {
            val container = buildContainer(payload) ?: return null
            return Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
                putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
                putExtra(PushConstants.MESSAGE_RECEIVE_TIME, receivedAtMs.toString())
                `package` = container.packageName
            }
        }

        @JvmStatic
        fun constructAckMessage(context: Context, container: XmPushActionContainer): XmPushActionContainer {
            val ackMessage = XmPushActionAckMessage().apply {
                setAppId(container.appid)
            }
            val metaInfo: PushMetaInfo? = container.metaInfo
            if (metaInfo != null) {
                ackMessage.setId(metaInfo.id)
                ackMessage.setMessageTs(metaInfo.messageTs)
                if (!TextUtils.isEmpty(metaInfo.topic)) {
                    ackMessage.setTopic(metaInfo.topic)
                }
            }
            ackMessage.setDeviceStatus(XmPushThriftSerializeUtils.getDeviceStatus(context, container))
            val ackContainer = MIPushHelper.generateRequestContainer(
                container.packageName,
                container.appid,
                ackMessage,
                ActionType.AckMessage,
            )
            val ackMetaInfo = container.metaInfo.deepCopy().apply {
                putToExtra(PushConstants.MESSAGE_ACK_TIME, System.currentTimeMillis().toString())
            }
            ackContainer.setMetaInfo(ackMetaInfo)
            return ackContainer
        }

        @JvmStatic
        @Throws(Throwable::class)
        fun postProcessMIPushMessage(pushAction: IPushServiceAction, targetPackage: String, payload: ByteArray, intent: Intent) {
            pushAction.runtimeObserver.postProcessMIPushMessage(targetPackage, payload, intent)
        }

        private fun processMIPushMessage(pushAction: IPushServiceAction, payload: ByteArray, trafficBytes: Long) {
            val startedAt = System.nanoTime()
            val container = buildContainer(payload)
            if (container == null) {
                pushAction.runtimeObserver.processMIPushMessage(payload, trafficBytes)
                MagiskOtel.event(
                    name = "push.dispatch",
                    attributes = mapOf(
                        "result" to "ok",
                        "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                        "process" to "push",
                        "stage" to "event_process",
                        "reason" to "container_null_fallback",
                        "payload_size" to trafficBytes.toString(),
                    ),
                    statusOk = true,
                )
                return
            }
            if (container.packageName.isNullOrEmpty()) {
                MyLog.e("receive a mipush message without package name")
                MIPushAckDispatcher.sendErrorAck(
                    pushAction,
                    container,
                    "empty_package_name",
                    "package name is empty",
                )
                return
            }
            if (shouldCheckProfile(container) && !pushAction.runtimeObserver.shouldAcceptProfile(container)) {
                MyLog.w(
                    "drop display message outside registered profile pkg=${container.packageName} " +
                        "messageId=${container.metaInfo?.id}",
                )
                MIPushAckDispatcher.sendProfileIdMismatchAck(pushAction, container)
                pushAction.runtimeObserver.onNotificationEvent(
                    container.packageName,
                    "profile_id_mismatch_drop",
                    "MIPushEventProcessor.processMIPushMessage",
                )
                MagiskOtel.event(
                    name = "push.dispatch",
                    attributes = mapOf(
                        "result" to "skip",
                        "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                        "process" to "push",
                        "stage" to "event_process",
                        "reason" to "profile_id_mismatch_drop",
                        "target_package" to container.packageName.orEmpty(),
                        "payload_size" to trafficBytes.toString(),
                    ),
                    statusOk = true,
                )
                return
            }
            if (dropForRegistrationStateIfRequired(pushAction, container)) {
                return
            }
            if (respondAwakeSystemAppProbeIfRequired(pushAction, container, trafficBytes, startedAt)) {
                return
            }
            if (consumeClearPushMessageIfRequired(pushAction, container, trafficBytes, startedAt)) {
                return
            }
            if (consumeSettingAppNotificationPermissionIfRequired(pushAction, container)) {
                return
            }
            if (consumeInboundControlAcksIfRequired(pushAction, container)) {
                return
            }
            if (routeSubscribeChannelNotifications(pushAction, container)) {
                return
            }
            observeLbsPushCommands(pushAction, container)
            if (routeCallKitMessageIfRequired(pushAction, container)) {
                return
            }
            pushAction.runtimeObserver.processMIPushMessage(payload, trafficBytes)
            maybeAckInboundSendMessage(pushAction, container, payload)
            MagiskOtel.event(
                name = "push.dispatch",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                    "process" to "push",
                    "stage" to "event_process",
                    "reason" to "processed",
                    "target_package" to container.packageName.orEmpty(),
                    "payload_size" to trafficBytes.toString(),
                    "action_type" to (container.action?.name ?: "unknown"),
                ),
                statusOk = true,
            )
        }

        /**
         * Stock invokes the raw x9.a profile matcher only in its display-message branch: a
         * SendMessage with non-empty title/description and passThrough != 1.
         */
        @JvmStatic
        fun shouldCheckProfile(container: XmPushActionContainer): Boolean {
            val metaInfo = container.metaInfo ?: return false
            return container.action == ActionType.SendMessage &&
                !metaInfo.title.isNullOrEmpty() &&
                !metaInfo.description.isNullOrEmpty() &&
                metaInfo.passThrough != 1
        }

        /**
         * Stock m0.h drops plain SendMessage payloads aimed at packages whose registration was
         * removed or whose push was switched off, and reports the drop back to the server with
         * the absent-target ack (s0). Without the gate the server never learns to stop pushing
         * to apps that asked to be silent.
         */
        private fun dropForRegistrationStateIfRequired(
            pushAction: IPushServiceAction,
            container: XmPushActionContainer,
        ): Boolean {
            val packageName = container.packageName ?: return false
            if (container.action != ActionType.SendMessage ||
                MIPushNotificationHelper.isBusinessMessage(container)
            ) {
                return false
            }
            val appInfo = MIPushAppInfo.getInstance(pushAction.context)
            val reason = when {
                appInfo.isUnRegistered(packageName) -> "unregistered"
                appInfo.isPushDisabled4User(packageName) -> "push_closed"
                else -> return false
            }
            MyLog.w("Drop a message for $reason package $packageName")
            MIPushAckDispatcher.sendAppAbsentAck(pushAction, container, packageName)
            pushAction.runtimeObserver.onNotificationEvent(
                packageName,
                "drop_message_$reason",
                "MIPushEventProcessor.dropForRegistrationStateIfRequired",
            )
            return true
        }

        /**
         * Stock 7.5.29 m0 consumes clear_push_message controls (ae.n.CancelPushMessage)
         * in the service dispatcher: u2.a refuses encrypted bodies, the cancel branch
         * resolves the wc.b/c/d/e matcher against the owning app's active notifications,
         * always acks through e1.b after the resolve attempt (miss -> errorCode 0 /
         * result_code 3; no matcher -> errorCode -1 / result_code -1 / cancelType 0;
         * the f1 verified-gone path -> result_code 1) and never forwards the control to
         * the app SDK (z7=false). Mirror that consumption here: vendor detects, decodes
         * and acks; the stock matcher routing is a product decision delegated to
         * [IPushRuntimeObserver.handleClearPushMessage].
         */
        @Suppress("ReturnCount")
        private fun consumeClearPushMessageIfRequired(
            pushAction: IPushServiceAction,
            container: XmPushActionContainer,
            trafficBytes: Long,
            startedAt: Long,
        ): Boolean {
            if (container.action != ActionType.Notification || !container.isRequest || container.isEncryptAction) {
                return false
            }
            val notification = decodeNotificationBody(container) ?: return false
            if (notification.type != NotificationType.CancelPushMessage.value) {
                return false
            }
            if (notification.extra == null) {
                // Stock: `!CancelPushMessage.equals(type) || c0Var2.extra == null` falls
                // into the unrecognized-type branch: consumed, no ack, no app forward.
                MyLog.w("clear_push_message without extras pkg=${container.packageName} messageId=${notification.id}")
                pushAction.runtimeObserver.onNotificationEvent(
                    container.packageName,
                    "clear_push_message_missing_extra",
                    "MIPushEventProcessor.consumeClearPushMessageIfRequired",
                )
                emitClearPushMessageOtel(
                    trafficBytes = trafficBytes,
                    startedAt = startedAt,
                    result = "skip",
                    reason = "clear_push_message_missing_extra",
                    targetPackage = container.packageName.orEmpty(),
                    matcher = "none",
                    handled = false,
                )
                return true
            }
            // Stock resolves against zVarB.packageName (the container target). The
            // decoded body is a vendor-owned copy, so apply the same fallback before
            // crossing the observer boundary (target pkg = notification.packageName
            // ?: container.packageName).
            if (notification.packageName.isNullOrEmpty()) {
                notification.packageName = container.packageName
            }
            val matcher = MIPushClearPushMessageSupport.resolveMatcher(notification.extra)
            val handled = try {
                pushAction.runtimeObserver.handleClearPushMessage(notification)
            } catch (t: Throwable) {
                MyLog.e(t)
                false
            }
            // Stock e1.b semantics: the ack is queued regardless of the match count.
            val errorCode = if (matcher == null) -1L else 0L
            val resultCode = when {
                matcher == null -> -1
                handled -> 1
                else -> 3
            }
            MIPushAckDispatcher.sendClearPushMessageAck(
                pushAction,
                container,
                notification,
                errorCode = errorCode,
                msgId = if (handled) matcher?.msgId?.takeIf { it.isNotEmpty() } else null,
                resultCode = resultCode,
                cancelType = matcher?.kind?.cancelType ?: 0,
            )
            pushAction.runtimeObserver.onNotificationEvent(
                container.packageName,
                "clear_push_message_consumed",
                "MIPushEventProcessor.consumeClearPushMessageIfRequired",
            )
            emitClearPushMessageOtel(
                trafficBytes = trafficBytes,
                startedAt = startedAt,
                result = "ok",
                reason = "clear_push_message_consumed",
                targetPackage = container.packageName.orEmpty(),
                matcher = matcher?.kind?.name?.lowercase() ?: "none",
                handled = handled,
            )
            return true
        }

        /** Mirrors stock u2.a for inbound Notification requests (the ae.c0 body shape). */
        @JvmStatic
        fun decodeNotificationBody(container: XmPushActionContainer): XmPushActionNotification? {
            val buffer = container.pushAction ?: return null
            val readOnly = buffer.asReadOnlyBuffer()
            if (readOnly.remaining() <= 0) {
                return null
            }
            val body = ByteArray(readOnly.remaining()).also { readOnly.get(it) }
            val notification = XmPushActionNotification()
            return try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(notification, body)
                notification
            } catch (t: Throwable) {
                MyLog.e(t)
                null
            }
        }

        private fun emitClearPushMessageOtel(
            trafficBytes: Long,
            startedAt: Long,
            result: String,
            reason: String,
            targetPackage: String,
            matcher: String,
            handled: Boolean,
        ) {
            MagiskOtel.event(
                name = "push.dispatch",
                attributes = mapOf(
                    "result" to result,
                    "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                    "process" to "push",
                    "stage" to "event_process",
                    "reason" to reason,
                    "target_package" to targetPackage,
                    "payload_size" to trafficBytes.toString(),
                    "action_type" to ActionType.Notification.name,
                    "clear_matcher" to matcher,
                    "clear_handled" to handled.toString(),
                ),
                statusOk = result != "error",
            )
        }

        /**
         * Stock 7.5.29 m0 consumes the subscribe-channel-sync control notifications inside the
         * service packet path: subscribe_channel_sync_result feeds the channel-config store and
         * is acked with subscribe_channel_sync_ack; sync_app_scene_michannel_result forwards
         * extra["scene_channel_data"] to the scenepush module (not ported here -> product side
         * logs and drops, no ack is invented). Vendor only decodes the notification envelope;
         * result parsing, storage and ack sending are product (observer) responsibilities.
         */
        private fun routeSubscribeChannelNotifications(
            pushAction: IPushServiceAction,
            container: XmPushActionContainer,
        ): Boolean {
            if (container.action != ActionType.Notification || container.pushAction == null) {
                return false
            }
            val notification = XmPushActionNotification()
            runCatching {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(
                    notification,
                    container.getPushAction(),
                )
            }.onFailure { return false }
            return when (notification.type) {
                NotificationType.SubscribeChannelSyncResult.value -> {
                    pushAction.runtimeObserver.onSubscribeChannelSyncResult(
                        container.packageName,
                        container.appid,
                        notification,
                    )
                    true
                }
                NotificationType.SyncAppSceneMiChannelResult.value -> {
                    pushAction.runtimeObserver.onSyncAppSceneMiChannelResult(
                        container.packageName,
                        notification.extra?.get("scene_channel_data"),
                    )
                    true
                }
                else -> false
            }
        }

        /**
         * Stock 7.5.29 m0 consumes the __check_alive/__awake pass-through probe (m0.g:527-557):
         * when the inbound metaInfo.extra carries both keys and the message is not a display
         * notification, the service answers with an awake_system_app notification response
         * (x0.f + x0.h) reporting whether the probe target package is currently running, plus
         * the echoed awaked flag when it is not. Stock's client-report of the "not awake"
         * event (xc.d.h with interface "9") is routed through the observation channel instead.
         * The stock ACTION_CHECK_ALIVE service intent (connection keep-alive ping) is a
         * different path handled by XMPushServiceIntentDelegate.handleCheckAlive; this wire
         * probe had no equivalent here before.
         */
        private fun respondAwakeSystemAppProbeIfRequired(
            pushAction: IPushServiceAction,
            container: XmPushActionContainer,
            trafficBytes: Long,
            startedAt: Long,
        ): Boolean {
            val metaInfo = container.metaInfo ?: return false
            val extra = metaInfo.extra ?: return false
            if (!extra.containsKey(PushConstants.EXTRA_PARAM_CHECK_ALIVE) ||
                !extra.containsKey(PushConstants.EXTRA_PARAM_AWAKE)
            ) {
                return false
            }
            // Stock gate: probes target the push service package itself.
            if (!PushConstants.PUSH_SERVICE_PACKAGE_NAME.contains(container.packageName.orEmpty())) {
                return false
            }
            // Same resolution as MIPushNotificationHelper.getTargetPackage (miui redirect for
            // service-addressed messages), without the TextUtils dependency.
            val targetPackage = if (
                PushConstants.PUSH_SERVICE_PACKAGE_NAME == container.packageName &&
                !extra[MIPushNotificationHelper.MIUI_PACKAGE_NAME].isNullOrEmpty()
            ) {
                extra[MIPushNotificationHelper.MIUI_PACKAGE_NAME].orEmpty()
            } else {
                container.packageName.orEmpty()
            }
            val appRunning = targetPackage.isNotEmpty() &&
                com.xiaomi.channel.commonutils.android.AppInfoUtils.isAppRunning(pushAction.context, targetPackage)
            val awaked = extra[PushConstants.EXTRA_PARAM_AWAKE]?.toBoolean() == true
            MIPushAckDispatcher.sendAwakeSystemAppResponse(
                pushAction,
                container,
                targetPackage,
                appRunning,
                awaked,
            )
            if (!appRunning && !awaked) {
                // Stock xc.d.h(pkg, type, msgId, "9") client-report fallback.
                pushAction.runtimeObserver.onNotificationEvent(
                    targetPackage,
                    "awake_probe_target_not_running",
                    "MIPushEventProcessor.respondAwakeSystemAppProbeIfRequired",
                )
            }
            MagiskOtel.event(
                name = "push.dispatch",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to (((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)).toString(),
                    "process" to "push",
                    "stage" to "event_process",
                    "reason" to "awake_probe_responded",
                    "target_package" to container.packageName.orEmpty(),
                    "payload_size" to trafficBytes.toString(),
                    "app_running" to appRunning.toString(),
                ),
                statusOk = true,
            )
            return true
        }

        /**
         * Stock 7.5.29 m0.g:619-622 consumes the setting_app_notification_permission control
         * (u2.a-decoded ae.c0 body, isRequest notification, not encrypted) and applies it in a
         * worker thread (u0.N -> com.xiaomi.push.service.c): d.a validates the extras, d.b
         * writes the notification app-ops switch through system privileges, and d.c always
         * answers the stock-shaped setting_app_notification_permission_ack with the wire
         * errorCode. Mirror that here: vendor detects and decodes, the product observer
         * validates/decides the code, and vendor sends the stock-shaped ack. The stock
         * f.n(xMPushService) self-package gate is kept: the control is only handled inside
         * the real com.xiaomi.xmsf package; elsewhere stock falls through unconsumed.
         */
        private fun consumeSettingAppNotificationPermissionIfRequired(
            pushAction: IPushServiceAction,
            container: XmPushActionContainer,
        ): Boolean {
            if (container.action != ActionType.Notification ||
                !container.isRequest ||
                container.isEncryptAction ||
                !PushConstants.PUSH_SERVICE_PACKAGE_NAME.equals(pushAction.context.packageName, true)
            ) {
                return false
            }
            val notification = decodeNotificationBody(container) ?: return false
            if (notification.type != NotificationType.SettingAppNotificationPermission.value) {
                return false
            }
            if (notification.packageName.isNullOrEmpty()) {
                notification.packageName = container.packageName
            }
            val result = try {
                pushAction.runtimeObserver.handleSettingAppNotificationPermission(notification)
            } catch (t: Throwable) {
                MyLog.e(t)
                null
            }
            if (result == null) {
                // Stock u0.N listener-absent path: log only, no ack is invented.
                MyLog.w(
                    "setting_app_notification_permission without a deciding handler " +
                        "pkg=${container.packageName} messageId=${notification.id}",
                )
                pushAction.runtimeObserver.onNotificationEvent(
                    container.packageName,
                    "setting_app_notification_permission_handler_absent",
                    "MIPushEventProcessor.consumeSettingAppNotificationPermissionIfRequired",
                )
                return true
            }
            MIPushAckDispatcher.sendSettingAppNotificationPermissionAck(
                pushAction,
                container,
                notification,
                errorCode = result.errorCode,
                reason = result.reason,
            )
            pushAction.runtimeObserver.onNotificationEvent(
                container.packageName,
                "setting_app_notification_permission_consumed",
                "MIPushEventProcessor.consumeSettingAppNotificationPermissionIfRequired",
            )
            return true
        }

        /**
         * Stock 7.5.29 m0.g:600-608 consumes the inbound control acks silently:
         * push_data_recover_ack feeds com.xiaomi.push.service.b.e (AppInfoRestorer merge of
         * the registered-app secret cache) and recover_lbs_subscription_ack feeds u0.m
         * (cc.r.f LBS subscription restore). Both are responses to uplink requests this tree
         * never sends, so a receipt means a server replay: consume without ack and without
         * app forward, exactly like stock's z7=false path.
         */
        private fun consumeInboundControlAcksIfRequired(
            pushAction: IPushServiceAction,
            container: XmPushActionContainer,
        ): Boolean {
            if (container.action != ActionType.Notification || container.isRequest || container.isEncryptAction) {
                return false
            }
            // Stock u2.a: a Notification response body decodes as the ae.c0 variant; the
            // control acks below are the ae.u (XmPushActionAckNotification) shape carried in
            // the same slot. Try the ack struct first, then fall through on any mismatch.
            val ack = decodeAckNotificationBody(container) ?: return false
            val event = when (ack.type) {
                NotificationType.PushDataForRecoverACK.value -> "push_data_recover_ack_consumed"
                NotificationType.RecoverLBSSubscriptionACK.value -> "recover_lbs_subscription_ack_consumed"
                else -> return false
            }
            MyLog.w("consume inbound control ack $event pkg=${container.packageName} messageId=${ack.id}")
            pushAction.runtimeObserver.onNotificationEvent(
                container.packageName,
                event,
                "MIPushEventProcessor.consumeInboundControlAcksIfRequired",
            )
            return true
        }

        /** Mirrors stock u2.a for Notification responses (the ae.u body shape). */
        @JvmStatic
        fun decodeAckNotificationBody(container: XmPushActionContainer): XmPushActionAckNotification? {
            val buffer = container.pushAction ?: return null
            val readOnly = buffer.asReadOnlyBuffer()
            if (readOnly.remaining() <= 0) {
                return null
            }
            val body = ByteArray(readOnly.remaining()).also { readOnly.get(it) }
            val ack = XmPushActionAckNotification()
            return try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(ack, body)
                ack
            } catch (t: Throwable) {
                MyLog.e(t)
                null
            }
        }

        /**
         * Stock 7.5.29 m0.g:482-493 decodes every inbound Command (u2.a -> ae.y) and routes
         * cmdName subscribe-lbs-push / unsubscribe-lbs-push to u0.X/u0.Y (cc.r LBS
         * subscription store), temp-storing the delivery intent via u0.L when the target app
         * is not running (cc.r.k pending-send replay). The cc LBS location stack is not part
         * of this port, so the vendor keeps only the detection + product observation and the
         * stock-shaped ack for command messages (standard AckMessage via m0.d). The message
         * still flows to the SDK like stock's pass-through broadcast; an encrypted command
         * body cannot be decoded here because it needs the unported mipush_apps_scrt store.
         */
        private fun observeLbsPushCommands(
            pushAction: IPushServiceAction,
            container: XmPushActionContainer,
        ) {
            if (container.action != ActionType.Command || container.isEncryptAction) {
                return
            }
            val command = decodeCommandBody(container) ?: return
            val cmdName = command.cmdName ?: return
            if (cmdName != COMMAND_SUBSCRIBE_LBS_PUSH && cmdName != COMMAND_UNSUBSCRIBE_LBS_PUSH) {
                return
            }
            pushAction.runtimeObserver.onLbsPushCommand(container.packageName, container.appid, cmdName)
            // Stock command flow is acked through the standard d(xMPushService, zVar, intent)
            // AckMessage in the m0 tail (line 790); reuse the existing ack plumbing.
            MIPushAckDispatcher.sendAckMessage(pushAction, container)
            MagiskOtel.event(
                name = "push.dispatch",
                attributes = mapOf(
                    "result" to "ok",
                    "duration_ms" to "0",
                    "process" to "push",
                    "stage" to "event_process",
                    "reason" to "lbs_push_command_observed",
                    "target_package" to container.packageName.orEmpty(),
                    "cmd_name" to cmdName,
                ),
                statusOk = true,
            )
        }

        /** Mirrors stock u2.a for Command containers (the ae.y body shape). */
        @JvmStatic
        fun decodeCommandBody(container: XmPushActionContainer): XmPushActionCommand? {
            val buffer = container.pushAction ?: return null
            val readOnly = buffer.asReadOnlyBuffer()
            if (readOnly.remaining() <= 0) {
                return null
            }
            val body = ByteArray(readOnly.remaining()).also { readOnly.get(it) }
            val command = XmPushActionCommand()
            return try {
                XmPushThriftSerializeUtils.convertByteArrayToThriftObject(command, body)
                command
            } catch (t: Throwable) {
                MyLog.e(t)
                null
            }
        }

        /**
         * Stock 7.5.29 m0.g:715-729 consumes a CallKit (hyper_type == "3") pass-through
         * notification: the CALLKIT_MSG_-prefixed z0 msg-id ring dedups by
         * (prefix+pkg -> msgId), a fresh message goes to u0.a (hc.a.c call-kit hand-off,
         * "0" on success -> u0.H timeout watch) and every failure or duplicate is reported
         * back with the stock error ack (m0.l -> b.b -> metaInfo error/reason). This tree has
         * no hc/ package, so the product observer logs + otels and returns the stock-shaped
         * failure result, which makes the vendor emit the same callkit_msg_handle_error ack
         * stock sends when the call-kit service is absent.
         */
        private fun routeCallKitMessageIfRequired(
            pushAction: IPushServiceAction,
            container: XmPushActionContainer,
        ): Boolean {
            if (container.action != ActionType.Notification || shouldCheckProfile(container)) {
                return false
            }
            val metaInfo = container.metaInfo ?: return false
            val extra = metaInfo.extra ?: return false
            if (extra.isEmpty() || extra[EXTRA_HYPER_TYPE] != CALLKIT_HYPER_TYPE) {
                return false
            }
            val packageName = container.packageName.orEmpty()
            MyLog.i("receive a callkit message. msgId:${metaInfo.id}")
            val duplicate = pushAction.runtimeObserver.isDuplicate(
                "$CALLKIT_MSG_ID_PREFIX$packageName",
                metaInfo.id.orEmpty(),
            )
            if (duplicate) {
                MyLog.i("received a duplicate callkit message. msgId:${metaInfo.id}")
                MIPushAckDispatcher.sendErrorAck(
                    pushAction,
                    container,
                    "duplicate_callkit_msg",
                    "received a duplicate callkit message",
                )
                pushAction.runtimeObserver.onNotificationEvent(
                    packageName,
                    "callkit_message_duplicate",
                    "MIPushEventProcessor.routeCallKitMessageIfRequired",
                )
                return true
            }
            val result = try {
                pushAction.runtimeObserver.handleCallKitMessage(packageName, container)
            } catch (t: Throwable) {
                MyLog.e(t)
                "1"
            }
            if (result == CALLKIT_RESULT_OK) {
                pushAction.runtimeObserver.onNotificationEvent(
                    packageName,
                    "callkit_message_routed",
                    "MIPushEventProcessor.routeCallKitMessageIfRequired",
                )
            } else {
                MIPushAckDispatcher.sendErrorAck(
                    pushAction,
                    container,
                    "callkit_msg_handle_error",
                    result,
                )
                pushAction.runtimeObserver.onNotificationEvent(
                    packageName,
                    "callkit_message_handle_error",
                    "MIPushEventProcessor.routeCallKitMessageIfRequired",
                )
            }
            return true
        }

        private fun maybeAckInboundSendMessage(
            pushAction: IPushServiceAction,
            container: XmPushActionContainer,
            payload: ByteArray,
        ) {
            if (container.action != ActionType.SendMessage || container.metaInfo == null) {
                return
            }
            if (!container.isEncryptAction || isHybridMessage(container)) {
                return
            }
            val missingTargetPackage = resolveMissingTargetPackage(pushAction.context, container)
            if (missingTargetPackage != null) {
                if (!container.appid.isNullOrBlank()) {
                    MIPushAckDispatcher.sendAppNotInstallNotification(pushAction, container, missingTargetPackage)
                }
                pushAction.runtimeObserver.onNotificationEvent(
                    missingTargetPackage,
                    "app_absent_ack_instead_of_normal_ack",
                    "MIPushEventProcessor.maybeAckInboundSendMessage",
                )
                return
            }
            // Stock m0 reports app_no_receiver when the target is installed but nothing can
            // receive the dispatch intent, instead of silently acking the message as delivered.
            val dispatchIntent = buildIntent(payload, System.currentTimeMillis())
            val receiverAbsent = dispatchIntent != null &&
                pushAction.context.packageManager
                    .queryBroadcastReceivers(dispatchIntent, 0)
                    .isNullOrEmpty()
            if (receiverAbsent) {
                MyLog.w("receive a mipush message, we can see the app, but we can't see the receiver.")
                MIPushAckDispatcher.sendErrorAck(
                    pushAction,
                    container,
                    "app_no_receiver",
                    "app has no receiver for the message",
                )
                pushAction.runtimeObserver.onNotificationEvent(
                    container.packageName,
                    "app_no_receiver_error_ack",
                    "MIPushEventProcessor.maybeAckInboundSendMessage",
                )
                return
            }
            val metaInfo = container.metaInfo
            metaInfo.putToExtra(PushConstants.MESSAGE_RECEIVE_TIME, System.currentTimeMillis().toString())
            MIPushAckDispatcher.sendAckMessage(pushAction, container)
        }

        @JvmStatic
        fun resolveMissingTargetPackage(context: Context, container: XmPushActionContainer): String? {
            val packageName = MIPushNotificationHelper.getTargetPackage(container)
            if (packageName.isNullOrBlank()) return null
            if (packageName == context.packageName) return null
            if (packageName == PushConstants.PUSH_SERVICE_PACKAGE_NAME) return null
            return try {
                context.packageManager.getPackageInfo(packageName, 0)
                null
            } catch (_: PackageManager.NameNotFoundException) {
                packageName
            } catch (t: RuntimeException) {
                MyLog.w("resolve target package failed: ${t.message}")
                null
            }
        }

        private fun isHybridMessage(container: XmPushActionContainer): Boolean {
            val action = container.metaInfo?.extra?.get(Constants.EXTRA_KEY_PUSH_SERVER_ACTION)
            return action == Constants.EXTRA_VALUE_HYBRID_MESSAGE ||
                action == Constants.EXTRA_VALUE_PLATFORM_MESSAGE
        }
    }
}
