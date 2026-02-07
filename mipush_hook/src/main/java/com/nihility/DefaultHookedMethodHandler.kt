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

class DefaultHookedMethodHandler : HookedMethodHandler {
    override fun shouldSendBroadcast(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean = joinPoint.proceed() as Boolean

    override fun postProcessMIPushMessage(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        pkgName: String,
        payload: ByteArray,
        newMessageIntent: Intent
    ) {
        joinPoint.proceed()
    }

    override fun notifyPacketArrival(joinPoint: JoinPoint, pushService: XMPushService, chid: String, data: Any) {}
    override fun debugLog(joinPoint: ProceedingJoinPoint): Any? = joinPoint.proceed()
    override fun logFallback(joinPoint: JoinPoint, fallback: Fallback, usePort: Boolean) {}
    override fun processIntent(joinPoint: JoinPoint, intent: Intent) {}
    override fun onCreate(joinPoint: JoinPoint, pushService: XMPushService) {}
    override fun onStartCommand(joinPoint: JoinPoint) {}
    override fun onStart(joinPoint: JoinPoint, intent: Intent, startId: Int) {}
    override fun onBind(joinPoint: JoinPoint, intent: Intent) {}
    override fun onDestroy(joinPoint: JoinPoint) {}
    override fun setConnectionStatus(joinPoint: JoinPoint, newStatus: Int, reason: Int, e: Exception) {}
    override fun sendMessage(joinPoint: JoinPoint, intent: Intent) {}
    override fun logCheckServices(joinPoint: JoinPoint, pkgInfo: PackageInfo) {}
    override fun buildIntent(joinPoint: ProceedingJoinPoint): Intent = joinPoint.proceed() as Intent
    override fun buildContainerHook(joinPoint: ProceedingJoinPoint): XmPushActionContainer =
        joinPoint.proceed() as XmPushActionContainer

    override fun isIntentAvailable(joinPoint: ProceedingJoinPoint): Boolean = joinPoint.proceed() as Boolean

    override fun processMIPushMessage(
        joinPoint: JoinPoint,
        pushService: XMPushService,
        decryptedContent: ByteArray,
        packetBytesLen: Long
    ) {
    }

    override fun isDuplicateMessage(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        packageName: String,
        messageId: String
    ): Boolean = joinPoint.proceed() as Boolean

    override fun notifyPushMessage(
        joinPoint: ProceedingJoinPoint,
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray
    ): MIPushNotificationHelper.NotifyPushMessageInfo =
        joinPoint.proceed() as MIPushNotificationHelper.NotifyPushMessageInfo
}
