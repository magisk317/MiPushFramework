package com.magisk317.push.hook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import com.magisk317.push.pipeline.MiPushRuntimeBridge
import com.magisk317.push.pipeline.MockMessageRegistry
import com.magisk317.service.XMPushServiceLifecycleBridge
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.XMPushService
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.aakira.napier.Napier
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
        val decision = BroadcastDecision.shouldSendBroadcast(pushService, packageName, container, metaInfo)
        AspectLogCompat.logShouldSendBroadcast(pushService, packageName, metaInfo, decision)
        return decision
    }

    @JvmStatic
    fun onBuildContainer(payloadSize: Int, container: XmPushActionContainer?) {
        HookTrace.mark("MIPushEventProcessor.buildContainer")
        AspectLogCompat.logBuildContainer(payloadSize, container)
    }

    @JvmStatic
    fun onBuildIntent(intent: Intent?, source: String) {
        HookTrace.mark("MIPushEventProcessor.buildIntent")
        AspectLogCompat.logBuildIntent(intent, source)
        intent?.removeExtra("messageId")
        intent?.removeExtra(ReportConstants.EVENT_MESSAGE_TYPE)
    }

    @JvmStatic
    fun onIntentAvailabilityChecked(intent: Intent?, available: Boolean, source: String) {
        HookTrace.mark("MIPushEventProcessor.isIntentAvailable")
        AspectLogCompat.logIntentAvailability(intent, available, source)
    }

    @JvmStatic
    fun postProcessMIPushMessage(
        pushService: XMPushService,
        pkgName: String,
        payload: ByteArray,
        newMessageIntent: Intent
    ) {
        HookTrace.mark("MIPushEventProcessor.postProcessMIPushMessage")
        AspectLogCompat.logPostProcessMIPushMessage(pkgName, payload.size, newMessageIntent)
        XMPushServiceLifecycleBridge.ensureCreated(pushService)
        newMessageIntent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
            ?.let { MiPushRuntimeBridge.onTransferToApplication(it) }
        MiPushRuntimeBridge.onTransferToApplication(payload)
    }

    @JvmStatic
    fun notifyPacketArrival(pushService: XMPushService, chid: String, data: Any) {
        HookTrace.mark("ClientEventDispatcher.notifyPacketArrival")
        AspectLogCompat.logPacketArrival(chid, data)
    }

    @JvmStatic
    fun logFallback(fallback: Fallback, usePort: Boolean) {
        HookTrace.mark("Fallback.getHosts")
        AspectLogCompat.logFallback(fallback, usePort)
    }

    @JvmStatic
    fun processIntent(intent: Intent) {
        HookTrace.mark("PushMessageProcessor.processIntent")
        AspectLogCompat.logProcessIntent(intent)
        val app = Utils.getApplication() ?: return
        MiPushRuntimeBridge.onApplicationIntentReceived(app, intent)
        MiPushRuntimeBridge.onIntentForwardedToServer(intent)
    }

    @JvmStatic
    fun onServiceCreate(pushService: XMPushService) {
        Napier.d("onServiceCreate called for $pushService", tag = "ExplicitHookBridge")
        HookTrace.mark("XMPushService.onCreate")
        AspectLogCompat.logServiceMethod("XMPushService.onCreate", details = "Service started")
        XMPushServiceLifecycleBridge.ensureCreated(pushService)
    }

    @JvmStatic
    fun onStartCommand() {
        HookTrace.mark("XMPushService.onStartCommand")
        AspectLogCompat.logServiceMethod("XMPushService.onStartCommand")
    }

    @JvmStatic
    fun onStartCommand(intent: Intent?) {
        Napier.d("onStartCommand called with intent: $intent", tag = "ExplicitHookBridge")
        HookTrace.mark("XMPushService.onStartCommand")
        AspectLogCompat.logServiceMethod("XMPushService.onStartCommand", intent)
    }

    @JvmStatic
    fun onStart(intent: Intent, startId: Int) {
        HookTrace.mark("XMPushService.onStart")
        AspectLogCompat.logServiceMethod("XMPushService.onStart", intent, "startId=$startId")
    }

    @JvmStatic
    fun onBind(intent: Intent) {
        HookTrace.mark("XMPushService.onBind")
        AspectLogCompat.logServiceMethod("XMPushService.onBind", intent)
    }

    @JvmStatic
    fun onDestroy() {
        HookTrace.mark("XMPushService.onDestroy")
        AspectLogCompat.logServiceMethod("XMPushService.onDestroy", details = "Service stopped")
        XMPushServiceLifecycleBridge.onDestroy(null)
    }

    @JvmStatic
    fun onConnectionStatusChanged(newStatus: Int, reason: Int, e: Exception) {
        HookTrace.mark("Connection.setConnectionStatus")
        AspectLogCompat.logServiceMethod(
            "Connection.setConnectionStatus",
            details = "newStatus=$newStatus reason=$reason error=${e.javaClass.simpleName}:${e.message}"
        )
    }

    @JvmStatic
    fun onConnectionStatusChanged(newStatus: Int, reason: Int) {
        HookTrace.mark("Connection.setConnectionStatus")
        AspectLogCompat.logServiceMethod(
            "Connection.setConnectionStatus",
            details = "newStatus=$newStatus reason=$reason"
        )
    }

    @JvmStatic
    fun onSendMessage(intent: Intent) {
        HookTrace.mark("XMPushService.sendMessage")
        AspectLogCompat.logServiceMethod("XMPushService.sendMessage", intent)
    }

    @JvmStatic
    fun onSendMessage(packageName: String, payloadSize: Int) {
        HookTrace.mark("XMPushService.sendMessage")
        AspectLogCompat.logServiceMethod(
            "XMPushService.sendMessage",
            details = "package=$packageName payloadSize=$payloadSize"
        )
    }

    @JvmStatic
    fun onManifestCheckServices(pkgInfo: PackageInfo) {
        HookTrace.mark("ManifestChecker.checkServices")
        AspectLogCompat.logManifestCheck(pkgInfo.packageName)
    }

    @JvmStatic
    fun processMIPushMessage(
        pushService: XMPushService,
        decryptedContent: ByteArray,
        packetBytesLen: Long,
        source: String = "ExplicitHookBridge.processMIPushMessage"
    ) {
        HookTrace.mark("MIPushEventProcessor.processMIPushMessage")
        AspectLogCompat.logProcessMIPushMessage(packetBytesLen, source)
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
        val isMockReplay = MockMessageRegistry.isMarked(messageId)
        if (isMockReplay) {
            // Mock replay should not be treated as duplicate; let message flow continue.
            AspectLogCompat.logDuplicateCheck(packageName, messageId, false)
            return false
        }
        val duplicated = DuplicateMessagePolicy.checkAndMark(messageId)
        AspectLogCompat.logDuplicateCheck(packageName, messageId, duplicated)
        return duplicated
    }

    @JvmStatic
    fun notifyPushMessage(context: Context, container: XmPushActionContainer, decryptedContent: ByteArray) {
        HookTrace.mark("MIPushNotificationHelper.notifyPushMessage")
        AspectLogCompat.logNotifyPushMessage(container, decryptedContent.size)
        MiPushRuntimeBridge.onNotificationDispatch(context, container, decryptedContent)
    }
}
