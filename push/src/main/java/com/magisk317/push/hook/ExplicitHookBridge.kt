package com.magisk317.push.hook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import com.magisk317.push.pipeline.MiPushRuntimeBridge
import com.magisk317.push.pipeline.MockMessageRegistry
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import top.trumeet.common.utils.Utils

/**
 * Explicit replacement for historical AspectJ join points.
 *
 * The old project used MethodHooker/Aspect weaving. After de-AOP, call sites
 * forward into this bridge so behavior stays discoverable and testable.
 */
object ExplicitHookBridge {
    @JvmStatic
    fun onClientReportClientInit() {
        HookTrace.mark("ClientReportClient")
    }

    @JvmStatic
    fun onPushClientReportCollectData() {
        HookTrace.mark("PushClientReportManager")
    }

    @JvmStatic
    fun onBridgeServiceCreate() {
        HookTrace.mark("com.xiaomi.xmsf.push.service.XMPushService.onCreate")
    }

    @JvmStatic
    fun onBridgeServiceDestroy() {
        HookTrace.mark("com.xiaomi.xmsf.push.service.XMPushService.onDestroy")
    }

    @JvmStatic
    fun shouldSendBroadcast(
        pushService: XMPushService,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean {
        HookTrace.mark("MIPushEventProcessor.shouldSendBroadcast")
        // Keep legacy behavior: do not block server-to-app broadcast.
        return true
    }

    @JvmStatic
    fun onBuildContainer(payloadSize: Int, container: XmPushActionContainer?) {
        HookTrace.mark("MIPushEventProcessor.buildContainer")
    }

    @JvmStatic
    fun onBuildIntent(intent: Intent?, source: String) {
        HookTrace.mark("MIPushEventProcessor.buildIntent")
    }

    @JvmStatic
    fun onIntentAvailabilityChecked(intent: Intent?, available: Boolean, source: String) {
        HookTrace.mark("MIPushEventProcessor.isIntentAvailable")
    }

    @JvmStatic
    fun postProcessMIPushMessage(
        pushService: XMPushService,
        pkgName: String,
        payload: ByteArray,
        newMessageIntent: Intent
    ) {
        HookTrace.mark("MIPushEventProcessor.postProcessMIPushMessage")
        MiPushRuntimeBridge.onTransferToApplication(payload)
    }

    @JvmStatic
    fun notifyPacketArrival(pushService: XMPushService, chid: String, data: Any) {
        HookTrace.mark("ClientEventDispatcher.notifyPacketArrival")
    }

    @JvmStatic
    fun logFallback(fallback: Fallback, usePort: Boolean) {
        HookTrace.mark("Fallback.getHosts")
    }

    @JvmStatic
    fun processIntent(intent: Intent) {
        HookTrace.mark("PushMessageProcessor.processIntent")
        val app = Utils.getApplication() ?: return
        MiPushRuntimeBridge.onApplicationIntentReceived(app, intent)
        MiPushRuntimeBridge.onIntentForwardedToServer(intent)
    }

    @JvmStatic
    fun onServiceCreate(pushService: XMPushService) {
        HookTrace.mark("XMPushService.onCreate")
    }

    @JvmStatic
    fun onStartCommand() {
        HookTrace.mark("XMPushService.onStartCommand")
    }

    @JvmStatic
    fun onStartCommand(intent: Intent?) {
        HookTrace.mark("XMPushService.onStartCommand")
    }

    @JvmStatic
    fun onStart(intent: Intent, startId: Int) {
        HookTrace.mark("XMPushService.onStart")
    }

    @JvmStatic
    fun onBind(intent: Intent) {
        HookTrace.mark("XMPushService.onBind")
    }

    @JvmStatic
    fun onDestroy() {
        HookTrace.mark("XMPushService.onDestroy")
    }

    @JvmStatic
    fun onConnectionStatusChanged(newStatus: Int, reason: Int, e: Exception) {
        HookTrace.mark("Connection.setConnectionStatus")
    }

    @JvmStatic
    fun onConnectionStatusChanged(newStatus: Int, reason: Int) {
        HookTrace.mark("Connection.setConnectionStatus")
    }

    @JvmStatic
    fun onSendMessage(intent: Intent) {
        HookTrace.mark("XMPushService.sendMessage")
    }

    @JvmStatic
    fun onSendMessage(packageName: String, payloadSize: Int) {
        HookTrace.mark("XMPushService.sendMessage")
    }

    @JvmStatic
    fun onManifestCheckServices(pkgInfo: PackageInfo) {
        HookTrace.mark("ManifestChecker.checkServices")
    }

    @JvmStatic
    fun processMIPushMessage(
        pushService: XMPushService,
        decryptedContent: ByteArray,
        packetBytesLen: Long,
        source: String = "ExplicitHookBridge.processMIPushMessage"
    ) {
        HookTrace.mark("MIPushEventProcessor.processMIPushMessage")
        MiPushRuntimeBridge.onPayloadFromServer(
            pushService,
            decryptedContent,
            packetBytesLen,
            source
        )
    }

    @JvmStatic
    fun isDuplicateMessage(
        pushService: XMPushService,
        packageName: String,
        messageId: String
    ): Boolean {
        HookTrace.mark("MiPushMessageDuplicate.isDuplicateMessage")
        return MockMessageRegistry.consumeIfMatched(messageId)
    }

    @JvmStatic
    fun notifyPushMessage(context: Context, container: XmPushActionContainer, decryptedContent: ByteArray) {
        HookTrace.mark("MIPushNotificationHelper.notifyPushMessage")
        MiPushRuntimeBridge.onNotificationDispatch(context, container, decryptedContent)
    }
}
