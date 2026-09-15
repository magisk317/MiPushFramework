package com.xiaomi.push.service

import android.app.Notification
import android.content.Context
import android.content.Intent
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionNotification

/**
 * Message-domain slice of [IPushRuntimeObserver]: inbound payload processing,
 * pending-message routing, packet/blob construction, subscribe-channel sync
 * and the stock 7.5.29 pass-through control decisions.
 */
interface IPushRuntimeMessageObserver {

    fun onPayloadReceived(context: Context, payload: ByteArray?, size: Long, source: String)
    fun shouldAcceptProfile(container: Any): Boolean = true
    fun processMIPushMessage(payload: ByteArray, trafficBytes: Long)

    /**
     * Stock 7.5.29 m0 consumes clear_push_message control notifications in the service
     * dispatcher and never forwards them to the app. Vendor detects the wire type,
     * decodes the body and sends the stock e1.b-shaped ack; the product layer maps the
     * stock wc.b/c/d/e matcher route onto the vendor notification-cache clear and
     * reports whether a cancel attempt matched (stock's found-vs-miss distinction in
     * the ack result code).
     */
    fun handleClearPushMessage(notification: XmPushActionNotification): Boolean = false
    fun postProcessMIPushMessage(targetPackage: String, payload: ByteArray, intent: Intent)
    fun onSendMessage(packageName: String, size: Int) = Unit
    fun notifyPacketArrival(chid: String, blob: Blob)
    fun notifyPacketArrival(chid: String, packet: Packet)
    fun constructBindBlob(client: Any): Blob? = null
    fun constructUnbindBlob(chid: String, userId: String): Blob? = null
    fun processPendingMessages(source: String, sender: IPendingPacketSender)
    fun processPendingRegistrationRequests(source: String, sender: IPendingPacketSender)
    fun removeCachedMsgId(msgId: String) {}
    fun isDuplicate(packageName: String, msgId: String): Boolean = false
    fun packToContainer(payload: ByteArray): Any? = null
    fun packToContainer(client: Any, packageName: String): Any? = null
    fun shouldSendBroadcast(context: Context, packageName: String, container: Any, metaInfo: Any?): Boolean = true
    
    fun processMIPushIntent(intent: Intent): Any?
    val notificationHandler: IPushNotificationHandler? get() = null
    fun onNotificationEvent(packageName: String?, event: String, source: String) {}

    /** Downlink subscribe_channel_sync_result; product stores the batch and answers the ack. */
    fun onSubscribeChannelSyncResult(sourcePackage: String?, sourceAppId: String?, notification: Any) {}
    /** Downlink sync_app_scene_michannel_result; scenepush module absent -> product logs and drops. */
    fun onSyncAppSceneMiChannelResult(sourcePackage: String?, sceneChannelData: String?) {}
    /** Internal com.xiaomi.push.SUB_GROUP_RESULT_REPORT; product pulls the reported app's channel config. */
    fun onSubGroupResultReport(packageName: String, requestId: String) {}
    fun rebuildRestoredNotification(context: Context, notification: Notification): Notification? = null


    /**
     * Stock 7.5.29 m0.g:715-729 consumes a non-display message whose metaInfo.extra
     * ["hyper_type"] == "3" (CallKit/VoIP) after the CALLKIT_MSG_ dedup ring: the control
     * goes to u0.a (hc.a.c -> com.os.callservice hand-off). The returned string is the
     * stock result code: "0" means handled; any other value is passed back to the server
     * verbatim as the stock-shaped callkit_msg_handle_error ack (m0.l -> b.b -> x0.h).
     * This tree has no hc/ package (no com.os.callservice integration), so the product
     * side logs + otels and reports a stock-shaped failure code, exactly like stock's
     * listener-absent contract (u0.a returns non-"0" -> error ack).
     */
    fun handleCallKitMessage(packageName: String, container: XmPushActionContainer): String = "1"

    /**
     * Stock 7.5.29 m0.g:619-622 consumes the setting_app_notification_permission control
     * (service-side d.a validation, d.b system app-ops application) and always answers the
     * stock-shaped setting_app_notification_permission_ack (d.c) when the dispatcher is
     * running inside the real com.xiaomi.xmsf package. Vendor detects, decodes and acks;
     * the product validates the extras per stock d.a and decides the wire errorCode.
     * Returning null mirrors the stock u0.N listener-absent path: log only, no ack.
     */
    fun handleSettingAppNotificationPermission(
        notification: XmPushActionNotification,
    ): PushSettingAppNotificationPermissionResult? = null

    /**
     * Stock 7.5.29 XMPushService.handleIntent:1557-1564 routes com.xiaomi.mipush.CLEAR_HEADSUPNOTIFICATION
     * to y0.b (heads-up stack listener clear). The com.xiaomi.push.headsup module is not
     * part of this port (same status as stock's null-listener path: product logs, otels
     * and drops).
     */
    fun onClearHeadsupNotificationRequested(packageName: String) {}

    /**
     * Stock 7.5.29 u0.D (package install, XMPushService.handleIntent:1494-1500): refresh
     * provider.g cache, subscribenotification AppSubManager.m(pkg) and scenepush e(pkg).
     * The product runs the per-package channel-config pull where an equivalent exists and
     * documents the absent provider.g/scenepush refreshes.
     */
    fun onPackageAdded(packageName: String) {}

    /**
     * Stock 7.5.29 u0.F (package update, XMPushService.handleIntent:1486-1491): refresh the
     * provider.g cache only. No provider.g equivalent exists in this tree, so the product
     * logs + otels the request.
     */
    fun onPackageReplaced(packageName: String) {}

    /**
     * Stock 7.5.29 m0.g:482-493 decodes an inbound Command (u2.a) and routes
     * cmdName subscribe-/unsubscribe-lbs-push to u0.X/u0.Y (cc.r LBS subscription store)
     * plus the u0.L temp-store-when-app-absent path (cc.r.k pending-send replay). The cc LBS
     * location stack is not ported: the product logs + otels and drops.
     */
    fun onLbsPushCommand(packageName: String?, appId: String?, cmdName: String) {}
}
