package io.github.magisk317.mipush.push.hook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import io.github.magisk317.mipush.hook.HookedMethodHandler
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.runtime.core.ConnectionStatus
import io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.runtime.PushRuntimeChannelTracker
import io.github.magisk317.mipush.common.utils.Utils

class ModernHookHandler : HookedMethodHandler {
    override fun shouldSendBroadcast(
        joinPoint: Any?,
        pushService: XMPushService,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean = ExplicitHookBridge.shouldSendBroadcast(pushService, packageName, container, metaInfo)

    override fun postProcessMIPushMessage(
        joinPoint: Any?,
        pushService: XMPushService,
        pkgName: String,
        payload: ByteArray,
        newMessageIntent: Intent
    ) {
        HookTraceCompat.postProcessMIPushMessage(pkgName, payload, newMessageIntent)
        XMPushServiceLifecycleBridge.ensureCreated(pushService)
        newMessageIntent.getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD)
            ?.let { MiPushRuntimeBridge.onTransferToApplication(it) }
        MiPushRuntimeBridge.onTransferToApplication(payload)
    }

    override fun notifyPacketArrival(joinPoint: Any?, pushService: XMPushService, chid: String, data: Any) {
        HookTraceCompat.notifyPacketArrival(chid, data)
    }

    override fun debugLog(joinPoint: Any?): Any? = null

    override fun logFallback(joinPoint: Any?, fallback: Fallback, usePort: Boolean) {
        HookTraceCompat.logFallback(fallback, usePort)
    }

    override fun processIntent(joinPoint: Any?, intent: Intent) {
        HookTraceCompat.processIntent(intent)
        val app = Utils.getApplication() ?: return
        MiPushRuntimeBridge.onApplicationIntentReceived(app, intent)
        MiPushRuntimeBridge.onIntentForwardedToServer(intent)
    }

    override fun onCreate(joinPoint: Any?, pushService: XMPushService) {
        HookTraceCompat.onServiceCreate(pushService)
        XMPushServiceLifecycleBridge.ensureCreated(pushService)
    }

    override fun onStartCommand(joinPoint: Any?) {
        HookTraceCompat.onStartCommand()
    }

    override fun onStart(joinPoint: Any?, intent: Intent, startId: Int) {
        HookTraceCompat.onStart(intent, startId)
    }

    override fun onBind(joinPoint: Any?, intent: Intent) {
        HookTraceCompat.onBind(intent)
    }

    override fun onDestroy(joinPoint: Any?) {
        HookTraceCompat.onDestroy()
        XMPushServiceLifecycleBridge.onDestroy(null)
    }

    override fun setConnectionStatus(joinPoint: Any?, newStatus: Int, reason: Int, e: Exception) {
        HookTraceCompat.onConnectionStatusChanged(newStatus, reason, e)
        XMPushServiceLifecycleBridge.onConnectionStatusChanged(ConnectionStatus.of(newStatus.coerceIn(0, 2)))
        PushRuntimeChannelTracker.observeConnectionState(
            newStatus = newStatus,
            reason = reason,
            source = "ModernHookHandler.setConnectionStatus"
        )
    }

    override fun sendMessage(joinPoint: Any?, intent: Intent) {
        HookTraceCompat.onSendMessage(intent)
    }

    override fun logCheckServices(joinPoint: Any?, pkgInfo: PackageInfo) {
        HookTraceCompat.onManifestCheckServices(pkgInfo)
    }

    override fun buildIntent(joinPoint: Any?): Intent = Intent()

    override fun buildContainerHook(joinPoint: Any?): XmPushActionContainer = XmPushActionContainer()

    override fun isIntentAvailable(joinPoint: Any?): Boolean = true

    override fun processMIPushMessage(
        joinPoint: Any?,
        pushService: XMPushService,
        decryptedContent: ByteArray,
        packetBytesLen: Long
    ) {
        HookTraceCompat.processMIPushMessage(packetBytesLen, "ModernHookHandler.processMIPushMessage")
        MiPushRuntimeBridge.onPayloadFromServer(
            pushService,
            decryptedContent,
            packetBytesLen,
            "ModernHookHandler.processMIPushMessage"
        )
    }

    override fun isDuplicateMessage(
        joinPoint: Any?,
        pushService: XMPushService,
        packageName: String,
        messageId: String
    ): Boolean = ExplicitHookBridge.isDuplicateMessage(pushService, packageName, messageId)

    override fun notifyPushMessage(
        joinPoint: Any?,
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray
    ): MIPushNotificationHelper.NotifyPushMessageInfo {
        HookTraceCompat.notifyPushMessage(container, decryptedContent)
        MiPushRuntimeBridge.onNotificationDispatch(context, container, decryptedContent)
        return MIPushNotificationHelper.NotifyPushMessageInfo()
    }
}
