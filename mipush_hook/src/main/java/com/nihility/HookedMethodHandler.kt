package com.nihility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint

interface HookedMethodHandler {
    @Throws(Throwable::class)
    fun shouldSendBroadcast(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean

    @Throws(Throwable::class)
    fun postProcessMIPushMessage(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        pkgName: String,
        payload: ByteArray,
        newMessageIntent: Intent
    )

    fun notifyPacketArrival(joinPoint: JoinPoint, pushService: XMPushService, chid: String, data: Any)
    @Throws(Throwable::class) fun debugLog(joinPoint: ProceedingJoinPoint): Any?
    fun logFallback(joinPoint: JoinPoint, fallback: Fallback, usePort: Boolean)
    fun processIntent(joinPoint: JoinPoint, intent: Intent)
    @Throws(Throwable::class) fun onCreate(joinPoint: JoinPoint, pushService: XMPushService)
    fun onStartCommand(joinPoint: JoinPoint)
    fun onStart(joinPoint: JoinPoint, intent: Intent, startId: Int)
    fun onBind(joinPoint: JoinPoint, intent: Intent)
    fun onDestroy(joinPoint: JoinPoint)
    fun setConnectionStatus(joinPoint: JoinPoint, newStatus: Int, reason: Int, e: Exception)
    fun sendMessage(joinPoint: JoinPoint, intent: Intent)
    fun logCheckServices(joinPoint: JoinPoint, pkgInfo: PackageInfo)
    @Throws(Throwable::class) fun buildIntent(joinPoint: ProceedingJoinPoint): Intent
    @Throws(Throwable::class) fun buildContainerHook(joinPoint: ProceedingJoinPoint): XmPushActionContainer
    @Throws(Throwable::class) fun isIntentAvailable(joinPoint: ProceedingJoinPoint): Boolean
    fun processMIPushMessage(
        joinPoint: JoinPoint,
        pushService: XMPushService,
        decryptedContent: ByteArray,
        packetBytesLen: Long
    )

    @Throws(Throwable::class)
    fun isDuplicateMessage(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        packageName: String,
        messageId: String
    ): Boolean

    @Throws(Throwable::class)
    fun notifyPushMessage(
        joinPoint: ProceedingJoinPoint,
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray
    ): MIPushNotificationHelper.NotifyPushMessageInfo
}
