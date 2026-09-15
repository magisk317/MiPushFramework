package io.github.magisk317.mipush.bridge

import android.app.Notification
import android.content.Context
import android.content.Intent
import com.xiaomi.channel.commonutils.android.AppInfoUtils
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.push.service.IPushNotificationHandler
import com.xiaomi.push.service.MIPushClearPushMessageSupport
import com.xiaomi.push.service.MIPushNotificationCacheSupport
import com.xiaomi.push.service.PushSettingAppNotificationPermissionResult
import com.xiaomi.push.service.XMPushServiceProxy
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import io.github.magisk317.mipush.common.compat.NotificationCompatBridge
import io.github.magisk317.mipush.push.hook.ExplicitHookBridge
import io.github.magisk317.mipush.push.hook.HookTraceCompat
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.core.PushRuntimeNotificationObservationSink
import io.github.magisk317.mipush.service.runtime.MIPushNotificationPublishHelper
import io.github.magisk317.mipush.service.runtime.AppPushMessageProcessor
import io.github.magisk317.xposed.logging.MagiskOtel

internal class MiPushRuntimeMessageNotificationExecutionAdapter(
    private val appContext: Context,
    private val notificationObservationSink: PushRuntimeNotificationObservationSink,
) {
    fun onApplicationIntentReceived(intent: Intent) {
        MiPushRuntimeBridge.onApplicationIntentReceived(appContext, intent)
    }

    fun onPayloadReceived(context: Context, payload: ByteArray?, size: Long, source: String) {
        payload?.let { MiPushRuntimeBridge.onPayloadFromServer(context, it, size, source) }
    }

    fun shouldAcceptProfile(container: Any): Boolean {
        val pushContainer = container as? XmPushActionContainer ?: return true
        return StockSurfaceSupport.isProfileAllowed(appContext, pushContainer)
    }

    fun processMIPushMessage(payload: ByteArray, trafficBytes: Long) {
        // Do not re-enter onPayloadFromServer here. The vendor dispatcher has already marked the
        // payload identity before calling this callback.
        MIPushNotificationPublishHelper.notifyPushMessage(
            context = appContext,
            decryptedContent = payload,
            dispatchMessageArrived = true,
        )
    }

    /**
     * Routes a vendor-detected clear_push_message control onto the stock wc.a matcher
     * family (7.5.29 m0 + wc.b/c/d/e) and reports whether a cancel attempt matched:
     * - notifyId (>= 0): stock wc.c/wc.d clear-by-id; the vendor cache support applies
     *   the same package-hashed id mapping as com.xiaomi.channel.commonutils.android.s.b.
     * - title+description: stock wc.e text clear (filter contains the rendered text).
     *
     * Gap: stock wc.b (msg_id only) matches the message id embedded in the posted
     * notification (n1.h) plus the u0.x/u0.g/u0.c msg-id registry, and stock wc.d
     * additionally verifies the embedded id. This tree has no msg-id ->
     * posted-notification registry, so msg-id-only controls report "not handled" and
     * the vendor acks exactly like a stock wc.b miss (errorCode 0, result_code 3,
     * cancelType 2). Do not invent the registry here.
     */
    fun handleClearPushMessage(
        notification: XmPushActionNotification,
        clearById: (String, Int) -> Int = { pkg, id -> MIPushNotificationCacheSupport.clearNotification(appContext, pkg, id) },
        clearByTitleDescription: (String, String, String) -> Int = { pkg, title, description ->
            MIPushNotificationCacheSupport.clearNotification(appContext, pkg, title, description)
        },
    ): Boolean {
        val packageName = notification.packageName?.takeIf { it.isNotEmpty() } ?: return false
        val matcher = MIPushClearPushMessageSupport.resolveMatcher(notification.extra) ?: return false
        return when (matcher.kind) {
            MIPushClearPushMessageSupport.MatcherKind.NOTIFY_ID,
            MIPushClearPushMessageSupport.MatcherKind.NOTIFY_ID_AND_MSG_ID,
            -> clearById(packageName, matcher.notifyId) > 0
            MIPushClearPushMessageSupport.MatcherKind.TITLE_DESCRIPTION ->
                clearByTitleDescription(packageName, matcher.title.orEmpty(), matcher.description.orEmpty()) > 0
            // Gap above: no msg-id -> posted-id registry in this tree (stock wc.b/u0.x).
            MIPushClearPushMessageSupport.MatcherKind.MSG_ID -> false
        }
    }

    fun postProcessMIPushMessage(targetPackage: String, payload: ByteArray, intent: Intent) {
        HookTraceCompat.processIntent(intent)
        frameworkProcessor().forwardToTargetApplication(appContext, payload)
    }

    fun notifyPacketArrival(chid: String, blob: Blob) {
        HookTraceCompat.processIntent(Intent("blob:$chid"))
    }

    fun notifyPacketArrival(chid: String, packet: Packet) {
        HookTraceCompat.processIntent(Intent("packet:$chid"))
    }

    fun cachePendingMessage(packageName: String, payload: ByteArray) {
        PushRuntimePendingPacketStore.addPendingMessage(packageName, payload)
    }

    fun addPendingMessage(packageName: String, payload: ByteArray) {
        PushRuntimePendingPacketStore.addPendingMessage(packageName, payload)
    }

    fun processPendingMessages(source: String, sender: com.xiaomi.push.service.IPendingPacketSender) {
        val pushAction = XMPushServiceProxy.get() ?: return
        PushRuntimePendingPacketStore.processPendingMessages(source, sender = { packageName, payload ->
            com.xiaomi.push.service.MIPushHelper.sendPacket(pushAction, appContext, packageName, payload)
        })
    }

    fun packToContainer(payload: ByteArray): Any? = XMPushUtils.packToContainer(payload)

    fun shouldSendBroadcast(
        context: Context,
        packageName: String,
        container: Any,
        metaInfo: Any?,
    ): Boolean {
        val pushService = MiPushRuntimeObserverBridge.currentService()
            ?: return true
        val xmContainer = container as? XmPushActionContainer ?: return true
        val xmMetaInfo = metaInfo as? com.xiaomi.xmpush.thrift.PushMetaInfo ?: return true
        return ExplicitHookBridge.shouldSendBroadcast(pushService, packageName, xmContainer, xmMetaInfo)
    }

    fun isDuplicate(packageName: String, msgId: String): Boolean {
        val pushService = MiPushRuntimeObserverBridge.currentService()
            ?: return false
        return ExplicitHookBridge.isDuplicateMessage(pushService, packageName, msgId)
    }

    fun processMIPushIntent(intent: Intent): Any? =
        com.xiaomi.mipush.sdk.PushMessageProcessor.getInstance(appContext).processIntent(intent)

    val notificationHandler: IPushNotificationHandler = object : IPushNotificationHandler {
        @Suppress("TooGenericExceptionCaught")
        override fun handleNotification(packageName: String, payload: ByteArray): Boolean {
            val startedAt = System.nanoTime()
            fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
                val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                val attrs = mutableMapOf(
                    "result" to result,
                    "duration_ms" to durationMs.toString(),
                    "process" to "main",
                    "target_package" to packageName,
                    "payload_size" to payload.size.toString(),
                )
                if (reason != null) {
                    attrs["reason"] = reason
                }
                MagiskOtel.event(
                    name = "push.receive",
                    attributes = attrs,
                    statusOk = statusOk,
                )
            }

            return try {
                MIPushNotificationPublishHelper.notifyPushMessage(appContext, payload)
                emit(result = "ok")
                true
            } catch (error: RuntimeException) {
                emit(
                    result = "error",
                    statusOk = false,
                    reason = error.javaClass.simpleName,
                )
                throw error
            }
        }

        override fun clearNotification(packageName: String, notifyId: Int) {
            // Current product layer clears notifications via runtime bridge + controller.
        }
    }

    fun onNotificationEvent(packageName: String?, event: String, source: String) {
        notificationObservationSink.observeNotificationEvent(packageName, event, source)
    }

    /**
     * Stock 7.5.29 routes a CallKit (metaInfo.extra["hyper_type"] == "3") pass-through
     * notification to u0.a -> hc.a.c, which hands the message to the system call service
     * (com.os.callservice) and returns "0" on success; every other result makes the vendor
     * send the stock callkit_msg_handle_error ack (m0.l). The hc/ package and the
     * com.os.callservice integration are not part of this port, so the product logs and
     * otels the interception attempt and reports the stock-shaped failure result. The vendor
     * then sends exactly the ack stock sends when no call-kit stack is available.
     */
    fun handleCallKitMessage(packageName: String, container: XmPushActionContainer): String {
        MyLog.w("callkit message intercepted but the hc call-kit stack is not ported; pkg=$packageName")
        MagiskOtel.event(
            name = "push.receive",
            attributes = mapOf(
                "result" to "skip",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "callkit",
                "reason" to "callkit_stack_not_ported",
                "target_package" to packageName,
                "message_id" to (container.metaInfo?.id.orEmpty()),
            ),
            statusOk = true,
        )
        // Stock u0.a listener-absent contract: any non-"0" value feeds the error ack reason.
        return "1"
    }

    /**
     * Stock 7.5.29 com.xiaomi.push.service.c/d applies the setting_app_notification_permission
     * control. This implementation keeps the full stock d.a validation (permissionType is
     * badge/ongoing, permissionStatus is "1"=personal or "0", appId and targetPackageName
     * present -> else errorCode 6 with the stock reason) and the d.b "target not installed"
     * errorCode 4, and otels the accepted request.
     *
     * Product decision gap: stock d.b writes the notification AppOps switch through
     * `n1.v` (system AppOpsManager privileges XMSF holds as a system app) and settles
     * errorCode 0/1/2/3 from the read-back. This build is not a system-privileged package
     * for those ops, so applying the switch is intentionally not implemented; the accepted
     * path answers errorCode 5 ("set switch error") with an explicit reason instead of
     * faking success. Do not change 5 to 0 without implementing the actual op write.
     */
    @Suppress("ReturnCount")
    fun handleSettingAppNotificationPermission(
        notification: XmPushActionNotification,
    ): PushSettingAppNotificationPermissionResult? {
        val extra = notification.extra
            ?: return PushSettingAppNotificationPermissionResult(6L, "notification extra is null")
        val permissionType = extra["permissionType"]
        val permissionStatus = extra["permissionStatus"]
        val targetPackageName = extra["targetPackageName"]
        if (extra["targetAppId"].isNullOrEmpty()) {
            return PushSettingAppNotificationPermissionResult(6L, "appId is null")
        }
        if (targetPackageName.isNullOrEmpty()) {
            return PushSettingAppNotificationPermissionResult(6L, "packageName is null")
        }
        if (permissionType != "badge" && permissionType != "ongoing") {
            return PushSettingAppNotificationPermissionResult(6L, "permissionType illegal : $permissionType")
        }
        // Stock: CertUserType.VALUE_PERSONAL ("1") or "0".
        if (permissionStatus != "1" && permissionStatus != "0") {
            return PushSettingAppNotificationPermissionResult(6L, "permissionStatus illegal : $permissionStatus")
        }
        if (!AppInfoUtils.isPkgInstalled(appContext, targetPackageName)) {
            return PushSettingAppNotificationPermissionResult(4L, null)
        }
        MyLog.w(
            "setting_app_notification_permission recorded for $targetPackageName " +
                "type=$permissionStatus but the AppOps switch write is not implemented",
        )
        MagiskOtel.event(
            name = "push.receive",
            attributes = mapOf(
                "result" to "skip",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "setting_app_notification_permission",
                "reason" to "app_ops_write_not_ported",
                "target_package" to targetPackageName,
                "permission_type" to permissionType.orEmpty(),
                "permission_status" to permissionStatus.orEmpty(),
            ),
            statusOk = true,
        )
        return PushSettingAppNotificationPermissionResult(
            errorCode = 5L,
            reason = "set switch error :AppOps notification permission application requires system privileges and is not implemented in this build",
        )
    }

    /**
     * Stock 7.5.29 XMPushService.handleIntent:1557-1564 -> y0.b forwards the clear request to
     * the heads-up stack listener (com.xiaomi.push.headsup). That module is not part of this
     * port, so the request is logged and dropped exactly like stock's null-listener path.
     */
    fun onClearHeadsupNotificationRequested(packageName: String) {
        MyLog.w("clear_headsupnotification requested for $packageName but the headsup module is not ported; dropping")
        MagiskOtel.event(
            name = "push.receive",
            attributes = mapOf(
                "result" to "skip",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "clear_headsupnotification",
                "reason" to "headsup_module_not_ported",
                "target_package" to packageName,
            ),
            statusOk = true,
        )
    }

    /**
     * Stock 7.5.29 m0.g:482-493 routes inbound Command cmdName subscribe-/unsubscribe-lbs-push
     * to u0.X/u0.Y (cc.r LBS subscription store) and temp-stores the delivery intent through
     * u0.L -> cc.r.k when the target app is not running. The cc LBS location stack is not part
     * of this port; the command is logged and otelled while the vendor continues the stock
     * pass-through flow (standard ack + SDK delivery).
     */
    fun onLbsPushCommand(packageName: String?, appId: String?, cmdName: String) {
        MyLog.w("lbs command $cmdName for pkg=$packageName received but the cc/* LBS stack is not ported")
        MagiskOtel.event(
            name = "push.receive",
            attributes = mapOf(
                "result" to "skip",
                "duration_ms" to "0",
                "process" to "xmsf",
                "stage" to "lbs_push_command",
                "reason" to "lbs_stack_not_ported",
                "target_package" to packageName.orEmpty(),
                "cmd_name" to cmdName,
                "app_id_present" to (!appId.isNullOrEmpty()).toString(),
            ),
            statusOk = true,
        )
    }


    fun rebuildRestoredNotification(context: Context, notification: Notification): Notification? =
        NotificationCompatBridge.buildSilencedRestoredNotification(context, notification)

    private fun frameworkProcessor(): AppPushMessageProcessor =
        io.github.magisk317.mipush.app.di.AppDependencies.get(appContext)
}
