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
import org.aspectj.lang.annotation.After
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.aspectj.lang.annotation.Before

@Aspect
class MethodHooker : HookedMethodHandler {
    private fun hookHandler(): HookedMethodHandler {
        val handler = Dependencies.instance()?.hookedMethodHandler()
        return handler ?: DefaultHookedMethodHandler()
    }

    @Around("execution(* com.xiaomi.push.service.MIPushEventProcessor.shouldSendBroadcast(..)) && args(pushService, packageName, container, metaInfo)")
    override fun shouldSendBroadcast(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean = hookHandler().shouldSendBroadcast(joinPoint, pushService, packageName, container, metaInfo)

    @Around("execution(* com.xiaomi.push.service.MIPushEventProcessor.postProcessMIPushMessage(..)) && args(pushService, pkgName, payload, newMessageIntent)")
    override fun postProcessMIPushMessage(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        pkgName: String,
        payload: ByteArray,
        newMessageIntent: Intent
    ) = hookHandler().postProcessMIPushMessage(joinPoint, pushService, pkgName, payload, newMessageIntent)

    @Before("execution(* com.xiaomi.push.service.ClientEventDispatcher.notifyPacketArrival(..)) && args(pushService, chid, data)")
    override fun notifyPacketArrival(joinPoint: JoinPoint, pushService: XMPushService, chid: String, data: Any) =
        hookHandler().notifyPacketArrival(joinPoint, pushService, chid, data)

    @Around(
        "(execution(* com.xiaomi.push.service.XMPushService*.*(..))" +
            " || execution(* com.xiaomi.push.service.PacketSync*.*(..))" +
            " || execution(* com.xiaomi.push.service.ClientEventDispatcher*.*(..))" +
            " || execution(* com.xiaomi.push.service.MIPushEventProcessor*.*(..))" +
            " || execution(* com.xiaomi.push.service.MIPushNotificationHelper*.*(..))" +
            " || execution(* com.xiaomi.push.service.NotificationManagerHelper*.*(..))" +
            ") && !within(is(FinalType))"
    )
    override fun debugLog(joinPoint: ProceedingJoinPoint): Any? = hookHandler().debugLog(joinPoint)

    @Before("execution(* com.xiaomi.network.Fallback.getHosts(..)) && target(fallback) && args(usePort)")
    override fun logFallback(joinPoint: JoinPoint, fallback: Fallback, usePort: Boolean) =
        hookHandler().logFallback(joinPoint, fallback, usePort)

    @Before("execution(* com.xiaomi.mipush.sdk.PushMessageProcessor.processIntent(..)) && args(intent)")
    override fun processIntent(joinPoint: JoinPoint, intent: Intent) = hookHandler().processIntent(joinPoint, intent)

    @After("execution(* com.xiaomi.push.service.XMPushService.onCreate(..)) && this(pushService)")
    override fun onCreate(joinPoint: JoinPoint, pushService: XMPushService) = hookHandler().onCreate(joinPoint, pushService)

    @Before("execution(* com.xiaomi.push.service.XMPushService.onStartCommand(..))")
    override fun onStartCommand(joinPoint: JoinPoint) = hookHandler().onStartCommand(joinPoint)

    @Before("execution(* com.xiaomi.push.service.XMPushService.onStart(..)) && args(intent, startId)")
    override fun onStart(joinPoint: JoinPoint, intent: Intent, startId: Int) =
        hookHandler().onStart(joinPoint, intent, startId)

    @Before("execution(* com.xiaomi.push.service.XMPushService.onBind(..)) && args(intent)")
    override fun onBind(joinPoint: JoinPoint, intent: Intent) = hookHandler().onBind(joinPoint, intent)

    @Before("execution(* com.xiaomi.push.service.XMPushService.onDestroy(..))")
    override fun onDestroy(joinPoint: JoinPoint) = hookHandler().onDestroy(joinPoint)

    @Before("execution(* com.xiaomi.smack.Connection.setConnectionStatus(..)) && args(newStatus, reason, e)")
    override fun setConnectionStatus(joinPoint: JoinPoint, newStatus: Int, reason: Int, e: Exception) =
        hookHandler().setConnectionStatus(joinPoint, newStatus, reason, e)

    @Before("execution(* com.xiaomi.push.service.XMPushService.sendMessage*(..)) && args(intent)")
    override fun sendMessage(joinPoint: JoinPoint, intent: Intent) = hookHandler().sendMessage(joinPoint, intent)

    @Before("execution(* com.xiaomi.mipush.sdk.ManifestChecker.checkServices(..)) && args(context, pkgInfo)")
    override fun logCheckServices(joinPoint: JoinPoint, pkgInfo: PackageInfo) = hookHandler().logCheckServices(joinPoint, pkgInfo)

    @Around("execution(* com.xiaomi.push.service.MIPushEventProcessor.buildIntent(..))")
    override fun buildIntent(joinPoint: ProceedingJoinPoint): Intent = hookHandler().buildIntent(joinPoint)

    @Around(
        "execution(* com.nihility.XMPushUtils.packToContainer(..))" +
            "|| execution(* com.xiaomi.push.service.MIPushEventProcessor.buildContainer(..))"
    )
    override fun buildContainerHook(joinPoint: ProceedingJoinPoint): XmPushActionContainer =
        hookHandler().buildContainerHook(joinPoint)

    @Around("execution(* com.xiaomi.push.service.MIPushEventProcessor.isIntentAvailable(..))")
    override fun isIntentAvailable(joinPoint: ProceedingJoinPoint): Boolean = hookHandler().isIntentAvailable(joinPoint)

    @Before("execution(* com.xiaomi.push.service.MIPushEventProcessor.processMIPushMessage(..)) && args(pushService, decryptedContent, packetBytesLen)")
    override fun processMIPushMessage(
        joinPoint: JoinPoint,
        pushService: XMPushService,
        decryptedContent: ByteArray,
        packetBytesLen: Long
    ) = hookHandler().processMIPushMessage(joinPoint, pushService, decryptedContent, packetBytesLen)

    @Around("execution(* com.xiaomi.push.service.MiPushMessageDuplicate.isDuplicateMessage(..)) &&args(pushService, packageName, messageId)")
    override fun isDuplicateMessage(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        packageName: String,
        messageId: String
    ): Boolean = hookHandler().isDuplicateMessage(joinPoint, pushService, packageName, messageId)

    @Around("execution(* com.xiaomi.push.service.MIPushNotificationHelper.notifyPushMessage(..)) &&args(context, container, decryptedContent)")
    override fun notifyPushMessage(
        joinPoint: ProceedingJoinPoint,
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray
    ): MIPushNotificationHelper.NotifyPushMessageInfo =
        hookHandler().notifyPushMessage(joinPoint, context, container, decryptedContent)
}
