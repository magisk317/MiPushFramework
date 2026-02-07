package com.nihility.utils

import android.widget.Toast
import com.elvishew.xlog.Logger
import com.elvishew.xlog.XLog
import com.nihility.XMPushUtils
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.push.service.MiPushMessageDuplicateAspect
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import top.trumeet.common.utils.Utils
import java.lang.reflect.InvocationTargetException

object MockMIPushMessage {
    private val logger: Logger = XLog.tag(MockMIPushMessage::class.java.simpleName).build()

    @JvmStatic
    fun mockProcessMIPushMessage(pushService: XMPushService, container: XmPushActionContainer) {
        try {
            MiPushMessageDuplicateAspect.markAsMock(container)
            invokeProcessMiPushMessage(pushService, container)
        } catch (e: Exception) {
            logger.e("mock notification failure: ", e)
            Utils.makeText(pushService, "failure", Toast.LENGTH_SHORT)
        }
    }

    @JvmStatic
    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        ClassNotFoundException::class
    )
    fun invokeProcessMiPushMessage(pushService: XMPushService, container: XmPushActionContainer) {
        val mockDecryptedContent = XMPushUtils.packToBytes(container)
        invokeProcessMiPushMessage(pushService, mockDecryptedContent)
    }

    @JvmStatic
    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        ClassNotFoundException::class
    )
    fun invokeProcessMiPushMessage(pushService: XMPushService, mockDecryptedContent: ByteArray) {
        JavaCalls.callStaticMethodOrThrow<Boolean>(
            MIPushEventProcessor::class.java.name,
            "processMIPushMessage",
            pushService,
            mockDecryptedContent,
            mockDecryptedContent.size.toLong()
        )
    }
}
