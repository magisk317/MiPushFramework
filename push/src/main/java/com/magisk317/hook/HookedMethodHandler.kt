package com.magisk317.hook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer

interface HookedMethodHandler {
    @Throws(Throwable::class)
    fun shouldSendBroadcast(
        joinPoint: Any?,
        pushService: XMPushService,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean

    @Throws(Throwable::class)
    fun postProcessMIPushMessage(
        joinPoint: Any?,
        pushService: XMPushService,
        pkgName: String,
        payload: ByteArray,
        newMessageIntent: Intent
    )

    fun notifyPacketArrival(joinPoint: Any?, pushService: XMPushService, chid: String, data: Any)
    @Throws(Throwable::class) fun debugLog(joinPoint: Any?): Any?
    fun logFallback(joinPoint: Any?, fallback: Fallback, usePort: Boolean)
    fun processIntent(joinPoint: Any?, intent: Intent)
    @Throws(Throwable::class) fun onCreate(joinPoint: Any?, pushService: XMPushService)
    fun onStartCommand(joinPoint: Any?)
    fun onStart(joinPoint: Any?, intent: Intent, startId: Int)
    fun onBind(joinPoint: Any?, intent: Intent)
    fun onDestroy(joinPoint: Any?)
    fun setConnectionStatus(joinPoint: Any?, newStatus: Int, reason: Int, e: Exception)
    fun sendMessage(joinPoint: Any?, intent: Intent)
    fun logCheckServices(joinPoint: Any?, pkgInfo: PackageInfo)
    @Throws(Throwable::class) fun buildIntent(joinPoint: Any?): Intent
    @Throws(Throwable::class) fun buildContainerHook(joinPoint: Any?): XmPushActionContainer
    @Throws(Throwable::class) fun isIntentAvailable(joinPoint: Any?): Boolean
    fun processMIPushMessage(
        joinPoint: Any?,
        pushService: XMPushService,
        decryptedContent: ByteArray,
        packetBytesLen: Long
    )

    @Throws(Throwable::class)
    fun isDuplicateMessage(
        joinPoint: Any?,
        pushService: XMPushService,
        packageName: String,
        messageId: String
    ): Boolean

    @Throws(Throwable::class)
    fun notifyPushMessage(
        joinPoint: Any?,
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray
    ): MIPushNotificationHelper.NotifyPushMessageInfo
}
