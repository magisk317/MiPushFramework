package com.xiaomi.push.service

import android.content.Context
import android.content.Intent
import android.os.Looper
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.smack.XMPPException
import com.xiaomi.xmsf.runtime.PendingPacketErrorNotifier
import com.xiaomi.xmsf.runtime.PendingPacketSender
import com.xiaomi.xmsf.runtime.PushRuntimePendingPacketStore

object MIPushClientManager {
    @JvmStatic
    fun addPendingMessages(packageName: String, payload: ByteArray) {
        PushRuntimePendingPacketStore.addPendingMessage(packageName, payload)
    }

    @JvmStatic
    fun notifyError(
        context: Context,
        packageName: String,
        payload: ByteArray,
        errorCode: Int,
        errorMessage: String,
    ) {
        val intent = Intent(PushConstants.MIPUSH_ACTION_ERROR).apply {
            setPackage(packageName)
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
            putExtra(PushConstants.MIPUSH_EXTRA_ERROR_CODE, errorCode)
            putExtra(PushConstants.MIPUSH_EXTRA_ERROR_MSG, errorMessage)
        }
        context.sendBroadcast(intent, MIPushHelper.getReceiverPermission(packageName))
    }

    @JvmStatic
    fun notifyRegisterError(context: Context, errorCode: Int, errorMessage: String) {
        PushRuntimePendingPacketStore.notifyRegisterError(
            errorCode,
            errorMessage,
            object : PendingPacketErrorNotifier {
                override fun notify(
                    packageName: String,
                    payload: ByteArray,
                    errorCode: Int,
                    errorMessage: String,
                ) {
                    notifyError(context, packageName, payload, errorCode, errorMessage)
                }
            },
        )
    }

    @JvmStatic
    fun processPendingMessages(pushService: XMPushService) {
        try {
            val isMainThread = Thread.currentThread() == Looper.getMainLooper().thread
            PushRuntimePendingPacketStore.processPendingMessages(
                "MIPushClientManager.processPendingMessages",
                object : PendingPacketSender {
                    override fun send(packageName: String, payload: ByteArray) {
                        try {
                            MIPushHelper.sendPacket(pushService, packageName, payload)
                            if (!isMainThread) {
                                try {
                                    Thread.sleep(100L)
                                } catch (_: InterruptedException) {
                                }
                            }
                        } catch (e: XMPPException) {
                            throw RuntimeException(e)
                        }
                    }
                },
            )
        } catch (e: RuntimeException) {
            val cause = e.cause
            if (cause is XMPPException) {
                MyLog.e("meet error when process pending message. $cause")
                pushService.disconnect(10, cause)
                return
            }
            throw e
        }
    }

    @JvmStatic
    fun processPendingRegistrationRequest(pushService: XMPushService) {
        try {
            PushRuntimePendingPacketStore.processPendingRegistrationRequests(
                "MIPushClientManager.processPendingRegistrationRequest",
                object : PendingPacketSender {
                    override fun send(packageName: String, payload: ByteArray) {
                        try {
                            MIPushHelper.sendPacket(pushService, packageName, payload)
                        } catch (e: XMPPException) {
                            throw RuntimeException(e)
                        }
                    }
                },
            )
        } catch (e: RuntimeException) {
            val cause = e.cause
            if (cause is XMPPException) {
                MyLog.e("fail to deal with pending register request. $cause")
                pushService.disconnect(10, cause)
                return
            }
            throw e
        }
    }

    @JvmStatic
    fun registerApp(packageName: String, payload: ByteArray) {
        PushRuntimePendingPacketStore.cacheRegistrationRequest(packageName, payload)
    }
}
