package com.xiaomi.push.service
import io.github.magisk317.mipush.protocol.model.*

import android.content.Context
import android.content.Intent
import android.os.Looper
import com.xiaomi.channel.commonutils.logger.MyLog
import com.xiaomi.smack.XMPPException
import com.xiaomi.smack.packet.Packet
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.apache.thrift.TBase

object MIPushClientManager {
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
        val observer = XMPushService.observer
        observer?.notifyRegisterError(errorCode, errorMessage, object : IPendingPacketErrorNotifier {
            override fun notifyError(errorCode: Int, errorMessage: String) {
                MIPushAckDispatcher.notifyRegisterError(context, errorCode, errorMessage)
            }
        })
    }

    @JvmStatic
    fun processPendingMessages(pushAction: IPushServiceAction, context: Context) {
        val isMainThread = Looper.getMainLooper().thread == Thread.currentThread()
        pushAction.runtimeObserver.processPendingMessages(
            "MIPushClientManager.processPendingMessages",
            object : IPendingPacketSender {
                override fun sendPacket(packet: Packet) {
                    try {
                        val packageName = packet.packageName
                        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(packet as TBase<*, *>)
                        if (payload != null) {
                            MIPushHelper.sendPacket(pushAction, context, packageName, payload)
                        }
                        if (!isMainThread) {
                            Thread.sleep(700L)
                        }
                    } catch (e: Exception) {
                        MyLog.e(e)
                    }
                }
            })
    }

    @JvmStatic
    fun processPendingRegistrationRequest(pushAction: IPushServiceAction, context: Context) {
        pushAction.runtimeObserver.processPendingRegistrationRequests(
            "MIPushClientManager.processPendingRegistrationRequest",
            object : IPendingPacketSender {
                override fun sendPacket(packet: Packet) {
                    try {
                        val packageName = packet.packageName
                        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(packet as TBase<*, *>)
                        if (payload != null) {
                            MIPushHelper.sendPacket(pushAction, context, packageName, payload)
                        }
                    } catch (e: Exception) {
                        MyLog.e(e)
                    }
                }
            })
    }

    @JvmStatic
    fun registerApp(pushAction: IPushServiceAction, packageName: String, payload: ByteArray) {
        pushAction.runtimeObserver.cacheRegistrationRequest(packageName, payload)
    }

    @JvmStatic
    fun addPendingMessages(pushAction: IPushServiceAction, packageName: String, payload: ByteArray) {
        pushAction.runtimeObserver.cachePendingMessage(packageName, payload)
    }
}
