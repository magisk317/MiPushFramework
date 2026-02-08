package com.nihility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import com.elvishew.xlog.XLog
import com.magisk317.push.pipeline.MiPushRuntimeBridge
import com.magisk317.push.pipeline.MockMessageRegistry
import com.nihility.utils.Singleton
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer

/**
 * Modern HookHandler implementation.
 * All AOP logic has been removed. Methods now do nothing or return default values.
 * This class remains to satisfy the interface requirements in case binary-woven 
 * Xiaomi SDK calls these methods.
 */
class HookHandler : HookedMethodHandler {
    override fun shouldSendBroadcast(
        joinPoint: Any?,
        pushService: XMPushService,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean = true

    override fun postProcessMIPushMessage(
        joinPoint: Any?,
        pushService: XMPushService,
        pkgName: String,
        payload: ByteArray,
        newMessageIntent: Intent
    ) {
        MiPushRuntimeBridge.onTransferToApplication(payload)
    }

    override fun notifyPacketArrival(
        joinPoint: Any?,
        pushService: XMPushService,
        chid: String,
        data: Any
    ) {
        // Dead code removed
    }

    override fun debugLog(joinPoint: Any?): Any? = null

    override fun logFallback(joinPoint: Any?, fallback: Fallback, usePort: Boolean) {
        // Dead code removed
    }

    override fun processIntent(joinPoint: Any?, intent: Intent) {
        val app = top.trumeet.common.utils.Utils.getApplication() ?: return
        MiPushRuntimeBridge.onApplicationIntentReceived(app, intent)
        MiPushRuntimeBridge.onIntentForwardedToServer(intent)
    }

    override fun onCreate(joinPoint: Any?, pushService: XMPushService) {
        // Dead code removed
    }

    override fun onStartCommand(joinPoint: Any?) {
        // Dead code removed
    }

    override fun onStart(joinPoint: Any?, intent: Intent, startId: Int) {
        // Dead code removed
    }

    override fun onBind(joinPoint: Any?, intent: Intent) {
        // Dead code removed
    }

    override fun onDestroy(joinPoint: Any?) {
        // Dead code removed
    }

    override fun setConnectionStatus(joinPoint: Any?, newStatus: Int, reason: Int, e: Exception) {
        // Dead code removed
    }

    override fun sendMessage(joinPoint: Any?, intent: Intent) {
        // Dead code removed
    }

    override fun logCheckServices(joinPoint: Any?, pkgInfo: PackageInfo) {
        // Dead code removed
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
        MiPushRuntimeBridge.onPayloadFromServer(
            pushService,
            decryptedContent,
            packetBytesLen,
            "HookHandler.processMIPushMessage"
        )
    }

    override fun isDuplicateMessage(
        joinPoint: Any?,
        pushService: XMPushService,
        packageName: String,
        messageId: String
    ): Boolean = MockMessageRegistry.consumeIfMatched(messageId)

    override fun notifyPushMessage(
        joinPoint: Any?,
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray
    ): MIPushNotificationHelper.NotifyPushMessageInfo {
        MiPushRuntimeBridge.onNotificationDispatch(context, container, decryptedContent)
        return MIPushNotificationHelper.NotifyPushMessageInfo()
    }

    companion object {
        private const val TAG = "HookHandler"
        private val logger = XLog.tag(TAG).build()
    }
}
