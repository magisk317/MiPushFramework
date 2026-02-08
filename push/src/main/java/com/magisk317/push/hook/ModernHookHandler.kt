package com.magisk317.push.hook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import com.magisk317.hook.HookedMethodHandler
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer

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
        ExplicitHookBridge.postProcessMIPushMessage(pushService, pkgName, payload, newMessageIntent)
    }

    override fun notifyPacketArrival(joinPoint: Any?, pushService: XMPushService, chid: String, data: Any) {
        ExplicitHookBridge.notifyPacketArrival(pushService, chid, data)
    }

    override fun debugLog(joinPoint: Any?): Any? = null

    override fun logFallback(joinPoint: Any?, fallback: Fallback, usePort: Boolean) {
        ExplicitHookBridge.logFallback(fallback, usePort)
    }

    override fun processIntent(joinPoint: Any?, intent: Intent) {
        ExplicitHookBridge.processIntent(intent)
    }

    override fun onCreate(joinPoint: Any?, pushService: XMPushService) {
        ExplicitHookBridge.onServiceCreate(pushService)
    }

    override fun onStartCommand(joinPoint: Any?) {
        ExplicitHookBridge.onStartCommand()
    }

    override fun onStart(joinPoint: Any?, intent: Intent, startId: Int) {
        ExplicitHookBridge.onStart(intent, startId)
    }

    override fun onBind(joinPoint: Any?, intent: Intent) {
        ExplicitHookBridge.onBind(intent)
    }

    override fun onDestroy(joinPoint: Any?) {
        ExplicitHookBridge.onDestroy()
    }

    override fun setConnectionStatus(joinPoint: Any?, newStatus: Int, reason: Int, e: Exception) {
        ExplicitHookBridge.onConnectionStatusChanged(newStatus, reason, e)
    }

    override fun sendMessage(joinPoint: Any?, intent: Intent) {
        ExplicitHookBridge.onSendMessage(intent)
    }

    override fun logCheckServices(joinPoint: Any?, pkgInfo: PackageInfo) {
        ExplicitHookBridge.onManifestCheckServices(pkgInfo)
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
        ExplicitHookBridge.processMIPushMessage(pushService, decryptedContent, packetBytesLen)
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
        ExplicitHookBridge.notifyPushMessage(context, container, decryptedContent)
        return MIPushNotificationHelper.NotifyPushMessageInfo()
    }
}
