package com.magisk317.hook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer

/**
 * Default implementation of HookedMethodHandler.
 * AspectJ dependence removed. All methods now do nothing or return defaults.
 */
class DefaultHookedMethodHandler : HookedMethodHandler {
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
    ) {}

    override fun notifyPacketArrival(joinPoint: Any?, pushService: XMPushService, chid: String, data: Any) {}
    override fun debugLog(joinPoint: Any?): Any? = null
    override fun logFallback(joinPoint: Any?, fallback: Fallback, usePort: Boolean) {}
    override fun processIntent(joinPoint: Any?, intent: Intent) {}
    override fun onCreate(joinPoint: Any?, pushService: XMPushService) {}
    override fun onStartCommand(joinPoint: Any?) {}
    override fun onStart(joinPoint: Any?, intent: Intent, startId: Int) {}
    override fun onBind(joinPoint: Any?, intent: Intent) {}
    override fun onDestroy(joinPoint: Any?) {}
    override fun setConnectionStatus(joinPoint: Any?, newStatus: Int, reason: Int, e: Exception) {}
    override fun sendMessage(joinPoint: Any?, intent: Intent) {}
    override fun logCheckServices(joinPoint: Any?, pkgInfo: PackageInfo) {}
    override fun buildIntent(joinPoint: Any?): Intent = Intent()
    override fun buildContainerHook(joinPoint: Any?): XmPushActionContainer = XmPushActionContainer()
    override fun isIntentAvailable(joinPoint: Any?): Boolean = true

    override fun processMIPushMessage(
        joinPoint: Any?,
        pushService: XMPushService,
        decryptedContent: ByteArray,
        packetBytesLen: Long
    ) {}

    override fun isDuplicateMessage(
        joinPoint: Any?,
        pushService: XMPushService,
        packageName: String,
        messageId: String
    ): Boolean = false

    override fun notifyPushMessage(
        joinPoint: Any?,
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray
    ): MIPushNotificationHelper.NotifyPushMessageInfo = MIPushNotificationHelper.NotifyPushMessageInfo()
}
