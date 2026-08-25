package io.github.magisk317.mipush.bridge

import android.app.Notification
import android.content.Context
import android.content.Intent
import com.xiaomi.push.service.IPushNotificationHandler
import com.xiaomi.push.service.XMPushServiceProxy
import com.xiaomi.xmsf.stock.StockSurfaceSupport
import com.xiaomi.slim.Blob
import com.xiaomi.smack.packet.Packet
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.common.compat.NotificationCompatBridge
import io.github.magisk317.mipush.push.hook.ExplicitHookBridge
import io.github.magisk317.mipush.push.hook.HookTraceCompat
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.push.pipeline.MiPushRuntimeBridge
import io.github.magisk317.mipush.runtime.PushRuntimePendingPacketStore
import io.github.magisk317.mipush.runtime.core.PushRuntimeNotificationObservationSink
import io.github.magisk317.mipush.service.runtime.MyMIPushNotificationHelper
import com.xiaomi.push.sdk.PushMessageProcessor
import io.github.magisk317.xposed.logging.MagiskOtel

internal class MiPushRuntimeMessageNotificationExecutionAdapter(
    private val appContext: Context,
    private val notificationObservationSink: PushRuntimeNotificationObservationSink,
) {
    fun onApplicationIntentReceived(intent: Intent) {
        MiPushRuntimeBridge.onApplicationIntentReceived(appContext, intent)
    }

    fun onPayloadReceived(context: Context, payload: ByteArray?, size: Long, source: String) {
        payload?.let { MiPushRuntimeBridge.onPayloadFromServer(context, it, size, source) }
    }

    fun shouldAcceptProfile(container: Any): Boolean {
        val pushContainer = container as? XmPushActionContainer ?: return true
        return StockSurfaceSupport.isProfileAllowed(appContext, pushContainer)
    }

    fun processMIPushMessage(payload: ByteArray, trafficBytes: Long) {
        // Do not re-enter onPayloadFromServer here. The vendor dispatcher has already marked the
        // payload identity before calling this callback.
        MyMIPushNotificationHelper.notifyPushMessage(
            context = appContext,
            decryptedContent = payload,
            dispatchMessageArrived = true,
        )
    }

    fun postProcessMIPushMessage(targetPackage: String, payload: ByteArray, intent: Intent) {
        HookTraceCompat.processIntent(intent)
        frameworkProcessor().forwardToTargetApplication(appContext, payload)
    }

    fun notifyPacketArrival(chid: String, blob: Blob) {
        HookTraceCompat.processIntent(Intent("blob:$chid"))
    }

    fun notifyPacketArrival(chid: String, packet: Packet) {
        HookTraceCompat.processIntent(Intent("packet:$chid"))
    }

    fun cachePendingMessage(packageName: String, payload: ByteArray) {
        PushRuntimePendingPacketStore.addPendingMessage(packageName, payload)
    }

    fun addPendingMessage(packageName: String, payload: ByteArray) {
        PushRuntimePendingPacketStore.addPendingMessage(packageName, payload)
    }

    fun processPendingMessages(source: String, sender: com.xiaomi.push.service.IPendingPacketSender) {
        val pushAction = XMPushServiceProxy.get() ?: return
        PushRuntimePendingPacketStore.processPendingMessages(source) { packageName, payload ->
            com.xiaomi.push.service.MIPushHelper.sendPacket(pushAction, appContext, packageName, payload)
        }
    }

    fun packToContainer(payload: ByteArray): Any? = XMPushUtils.packToContainer(payload)

    fun shouldSendBroadcast(
        context: Context,
        packageName: String,
        container: Any,
        metaInfo: Any?,
    ): Boolean {
        val pushService = io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.peekService()
            ?: return true
        val xmContainer = container as? XmPushActionContainer ?: return true
        val xmMetaInfo = metaInfo as? com.xiaomi.xmpush.thrift.PushMetaInfo ?: return true
        return ExplicitHookBridge.shouldSendBroadcast(pushService, packageName, xmContainer, xmMetaInfo)
    }

    fun isDuplicate(packageName: String, msgId: String): Boolean {
        val pushService = io.github.magisk317.mipush.service.XMPushServiceLifecycleBridge.peekService()
            ?: return false
        return ExplicitHookBridge.isDuplicateMessage(pushService, packageName, msgId)
    }

    fun processMIPushIntent(intent: Intent): Any? =
        com.xiaomi.mipush.sdk.PushMessageProcessor.getInstance(appContext).processIntent(intent)

    val notificationHandler: IPushNotificationHandler = object : IPushNotificationHandler {
        @Suppress("TooGenericExceptionCaught")
        override fun handleNotification(packageName: String, payload: ByteArray): Boolean {
            val startedAt = System.nanoTime()
            fun emit(result: String, statusOk: Boolean = true, reason: String? = null) {
                val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
                val attrs = mutableMapOf(
                    "result" to result,
                    "duration_ms" to durationMs.toString(),
                    "process" to "main",
                    "target_package" to packageName,
                    "payload_size" to payload.size.toString(),
                )
                if (reason != null) {
                    attrs["reason"] = reason
                }
                MagiskOtel.event(
                    name = "push.receive",
                    attributes = attrs,
                    statusOk = statusOk,
                )
            }

            return try {
                MyMIPushNotificationHelper.notifyPushMessage(appContext, payload)
                emit(result = "ok")
                true
            } catch (error: RuntimeException) {
                emit(
                    result = "error",
                    statusOk = false,
                    reason = error.javaClass.simpleName,
                )
                throw error
            }
        }

        override fun clearNotification(packageName: String, notifyId: Int) {
            // Current product layer clears notifications via runtime bridge + controller.
        }
    }

    fun onNotificationEvent(packageName: String?, event: String, source: String) {
        notificationObservationSink.observeNotificationEvent(packageName, event, source)
    }

    fun rebuildRestoredNotification(context: Context, notification: Notification): Notification? =
        NotificationCompatBridge.buildSilencedRestoredNotification(context, notification)

    private fun frameworkProcessor(): PushMessageProcessor =
        io.github.magisk317.mipush.app.di.AppDependencies.get(appContext)
}
