package io.github.magisk317.mipush.push.hook

import android.content.Intent
import android.content.pm.PackageInfo
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.push.service.clientReport.ReportConstants
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import co.touchlab.kermit.Logger

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
        HookTrace.mark("com.xiaomi.xmsf.push.service.XMPushServiceCore.onCreate")
    }

    @JvmStatic
    fun onBridgeServiceDestroy() {
        HookTrace.mark("com.xiaomi.xmsf.push.service.XMPushServiceCore.onDestroy")
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
    fun onServiceCreate(pushService: XMPushServiceCore) {
        Logger.withTag("HookTraceCompat").d { "onServiceCreate called for $pushService" }
        HookTrace.mark("XMPushServiceCore.onCreate")
        AspectLogCompat.logServiceMethod("XMPushServiceCore.onCreate", details = "Service started")
    }

    @JvmStatic
    fun onStartCommand() {
        HookTrace.mark("XMPushServiceCore.onStartCommand")
        AspectLogCompat.logServiceMethod("XMPushServiceCore.onStartCommand")
    }

    @JvmStatic
    fun onStartCommand(intent: Intent?) {
        Logger.withTag("HookTraceCompat").d { "onStartCommand called with intent: $intent" }
        HookTrace.mark("XMPushServiceCore.onStartCommand")
        AspectLogCompat.logServiceMethod("XMPushServiceCore.onStartCommand", intent)
    }

    @JvmStatic
    fun onStart(intent: Intent, startId: Int) {
        HookTrace.mark("XMPushServiceCore.onStart")
        AspectLogCompat.logServiceMethod("XMPushServiceCore.onStart", intent, "startId=$startId")
    }

    @JvmStatic
    fun onBind(intent: Intent) {
        HookTrace.mark("XMPushServiceCore.onBind")
        AspectLogCompat.logServiceMethod("XMPushServiceCore.onBind", intent)
    }

    @JvmStatic
    fun onDestroy() {
        HookTrace.mark("XMPushServiceCore.onDestroy")
        AspectLogCompat.logServiceMethod("XMPushServiceCore.onDestroy", details = "Service stopped")
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
        HookTrace.mark("XMPushServiceCore.sendMessage")
        AspectLogCompat.logServiceMethod("XMPushServiceCore.sendMessage", intent)
    }

    @JvmStatic
    fun onSendMessage(packageName: String, payloadSize: Int) {
        HookTrace.mark("XMPushServiceCore.sendMessage")
        AspectLogCompat.logServiceMethod(
            "XMPushServiceCore.sendMessage",
            details = "package=$packageName payloadSize=$payloadSize"
        )
    }

    @JvmStatic
    fun onManifestCheckServices(pkgInfo: PackageInfo) {
        HookTrace.mark("ManifestChecker.checkServices", logEvent = false)
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
