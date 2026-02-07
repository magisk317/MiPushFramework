package com.nihility

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import com.elvishew.xlog.XLog
import com.nihility.utils.Singleton
import com.xiaomi.mipush.sdk.LogPushMessageProcessorAspect
import com.xiaomi.mipush.sdk.ManifestCheckerAspectLog
import com.xiaomi.network.Fallback
import com.xiaomi.network.LogFallbackAspect
import com.xiaomi.push.service.LogClientEventDispatcherAspect
import com.xiaomi.push.service.LogDebugAspect
import com.xiaomi.push.service.LogXMPushServiceAspect
import com.xiaomi.push.service.MIPushEventProcessorAspect
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.MIPushNotificationHelperAspect
import com.xiaomi.push.service.MiPushMessageDuplicateAspect
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import org.aspectj.lang.JoinPoint
import org.aspectj.lang.ProceedingJoinPoint

class HookHandler : HookedMethodHandler {
    override fun shouldSendBroadcast(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean = Singleton.instance<MIPushEventProcessorAspect>()
        .shouldSendBroadcast(joinPoint, pushService, packageName, container, metaInfo)

    override fun postProcessMIPushMessage(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        pkgName: String,
        payload: ByteArray,
        newMessageIntent: Intent
    ) = Singleton.instance<MIPushEventProcessorAspect>()
        .postProcessMIPushMessage(joinPoint, pushService, pkgName, payload, newMessageIntent)

    override fun notifyPacketArrival(
        joinPoint: JoinPoint,
        pushService: XMPushService,
        chid: String,
        data: Any
    ) = Singleton.instance<LogClientEventDispatcherAspect>()
        .notifyPacketArrival(joinPoint, pushService, chid, data)

    override fun debugLog(joinPoint: ProceedingJoinPoint): Any? =
        Singleton.instance<LogDebugAspect>().logger(joinPoint)

    override fun logFallback(joinPoint: JoinPoint, fallback: Fallback, usePort: Boolean) =
        Singleton.instance<LogFallbackAspect>().logFallback(joinPoint, fallback, usePort)

    override fun processIntent(joinPoint: JoinPoint, intent: Intent) =
        Singleton.instance<LogPushMessageProcessorAspect>().processIntent(joinPoint, intent)

    override fun onCreate(joinPoint: JoinPoint, pushService: XMPushService) =
        Singleton.instance<LogXMPushServiceAspect>().onCreate(joinPoint, pushService)

    override fun onStartCommand(joinPoint: JoinPoint) =
        Singleton.instance<LogXMPushServiceAspect>().onStartCommand(joinPoint)

    override fun onStart(joinPoint: JoinPoint, intent: Intent, startId: Int) =
        Singleton.instance<LogXMPushServiceAspect>().onStart(joinPoint, intent, startId)

    override fun onBind(joinPoint: JoinPoint, intent: Intent) =
        Singleton.instance<LogXMPushServiceAspect>().onBind(joinPoint, intent)

    override fun onDestroy(joinPoint: JoinPoint) =
        Singleton.instance<LogXMPushServiceAspect>().onDestroy(joinPoint)

    override fun setConnectionStatus(joinPoint: JoinPoint, newStatus: Int, reason: Int, e: Exception) =
        Singleton.instance<LogXMPushServiceAspect>().setConnectionStatus(joinPoint, newStatus, reason, e)

    override fun sendMessage(joinPoint: JoinPoint, intent: Intent) =
        Singleton.instance<LogXMPushServiceAspect>().sendMessage(joinPoint, intent)

    override fun logCheckServices(joinPoint: JoinPoint, pkgInfo: PackageInfo) =
        Singleton.instance<ManifestCheckerAspectLog>().logCheckServices(joinPoint, pkgInfo)

    override fun buildIntent(joinPoint: ProceedingJoinPoint): Intent =
        Singleton.instance<MIPushEventProcessorAspect>().buildIntent(joinPoint)

    override fun buildContainerHook(joinPoint: ProceedingJoinPoint): XmPushActionContainer =
        requireNotNull(Singleton.instance<MIPushEventProcessorAspect>().buildContainerHook(joinPoint))

    override fun isIntentAvailable(joinPoint: ProceedingJoinPoint): Boolean =
        Singleton.instance<MIPushEventProcessorAspect>().isIntentAvailable(joinPoint)

    override fun processMIPushMessage(
        joinPoint: JoinPoint,
        pushService: XMPushService,
        decryptedContent: ByteArray,
        packetBytesLen: Long
    ) = Singleton.instance<MIPushEventProcessorAspect>()
        .processMIPushMessage(joinPoint, pushService, decryptedContent, packetBytesLen)

    override fun isDuplicateMessage(
        joinPoint: ProceedingJoinPoint,
        pushService: XMPushService,
        packageName: String,
        messageId: String
    ): Boolean = Singleton.instance<MiPushMessageDuplicateAspect>()
        .isDuplicateMessage(joinPoint, pushService, packageName, messageId)

    override fun notifyPushMessage(
        joinPoint: ProceedingJoinPoint,
        context: Context,
        container: XmPushActionContainer,
        decryptedContent: ByteArray
    ): MIPushNotificationHelper.NotifyPushMessageInfo =
        Singleton.instance<MIPushNotificationHelperAspect>()
            .notifyPushMessage(joinPoint, context, container, decryptedContent)

    companion object {
        private const val TAG = "HookHandler"
        private val logger = XLog.tag(TAG).build()
    }
}
