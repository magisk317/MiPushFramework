package io.github.magisk317.mipush.hook

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import com.xiaomi.network.Fallback
import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer

interface HookedMethodHandler {
    @Throws(Throwable::class)
    fun shouldSendBroadcast(
        joinPoint: Any?,
        pushService: XMPushServiceCore,
        packageName: String,
        container: XmPushActionContainer,
        metaInfo: PushMetaInfo
    ): Boolean

    @Throws(Throwable::class)
    fun postProcessMIPushMessage(
        joinPoint: Any?,
        pushService: XMPushServiceCore,
        pkgName: String,
        payload: ByteArray,
        newMessageIntent: Intent
    )

    fun notifyPacketArrival(joinPoint: Any?, pushService: XMPushServiceCore, chid: String, data: Any)
    @Throws(Throwable::class) fun debugLog(joinPoint: Any?): Any?
    fun logFallback(joinPoint: Any?, fallback: Fallback, usePort: Boolean)
    fun processIntent(joinPoint: Any?, intent: Intent)
    @Throws(Throwable::class) fun onCreate(joinPoint: Any?, pushService: XMPushServiceCore)
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
        pushService: XMPushServiceCore,
        decryptedContent: ByteArray,
        packetBytesLen: Long
    )

    @Throws(Throwable::class)
    fun isDuplicateMessage(
        joinPoint: Any?,
        pushService: XMPushServiceCore,
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
