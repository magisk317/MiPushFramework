package io.github.magisk317.mipush.push.hook

import android.content.Intent
import android.content.pm.PackageInfo
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.XMPushService
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.aakira.napier.Napier

object HookTraceCompat {
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
    fun onBuildContainer(payloadSize: Int, container: XmPushActionContainer?) {
        HookTrace.mark("MIPushEventProcessor.buildContainer")
        AspectLogCompat.logBuildContainer(payloadSize, container)
    }

    @JvmStatic
    fun postProcessMIPushMessage(pkgName: String, payload: ByteArray, newMessageIntent: Intent) {
        HookTrace.mark("MIPushEventProcessor.postProcessMIPushMessage")
        AspectLogCompat.logPostProcessMIPushMessage(pkgName, payload.size, newMessageIntent)
    }

    @JvmStatic
    fun notifyPacketArrival(chid: String, data: Any) {
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
    }

    @JvmStatic
    fun onServiceCreate(pushService: XMPushService) {
        Napier.d("onServiceCreate called for $pushService", tag = "HookTraceCompat")
        HookTrace.mark("XMPushService.onCreate")
        AspectLogCompat.logServiceMethod("XMPushService.onCreate", details = "Service started")
    }

    @JvmStatic
    fun onStartCommand() {
        HookTrace.mark("XMPushService.onStartCommand")
        AspectLogCompat.logServiceMethod("XMPushService.onStartCommand")
    }

    @JvmStatic
    fun onStartCommand(intent: Intent?) {
        Napier.d("onStartCommand called with intent: $intent", tag = "HookTraceCompat")
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
    @JvmOverloads
    fun processMIPushMessage(packetBytesLen: Long, source: String = "HookTraceCompat.processMIPushMessage") {
        HookTrace.mark("MIPushEventProcessor.processMIPushMessage")
        AspectLogCompat.logProcessMIPushMessage(packetBytesLen, source)
    }

    @JvmStatic
    fun notifyPushMessage(container: XmPushActionContainer, decryptedContent: ByteArray) {
        HookTrace.mark("MIPushNotificationHelper.notifyPushMessage")
        AspectLogCompat.logNotifyPushMessage(container, decryptedContent.size)
    }

    @JvmStatic
    fun onBuildIntent(intent: Intent?, source: String) {
        HookTrace.mark("MIPushEventProcessor.buildIntent")
        AspectLogCompat.logBuildIntent(intent, source)
        intent?.removeExtra("messageId")
        intent?.removeExtra(ReportConstants.EVENT_MESSAGE_TYPE)
    }
}
