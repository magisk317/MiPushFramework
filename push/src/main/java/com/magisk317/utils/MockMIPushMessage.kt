package com.magisk317.utils

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import com.magisk317.SdkNotificationCompat
import com.magisk317.XMPushUtils
import com.magisk317.push.pipeline.MockMessageRegistry
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import java.lang.reflect.InvocationTargetException

object MockMIPushMessage {
    private val TAG = MockMIPushMessage::class.java.simpleName
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = TAG)
        fun w(msg: String) = Napier.w(msg, tag = TAG)
        fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG)
    }

    @JvmStatic
    fun mockProcessMIPushMessage(pushService: XMPushService, container: XmPushActionContainer): Boolean {
        val payload = XMPushUtils.packToBytes(container)
        if (SdkNotificationCompat.shouldUseModernHelper(payload)) {
            logger.d(
                "mockProcessMIPushMessage use modern helper pkg=${container.packageName} action=${container.action}"
            )
            return runCatching {
                SdkNotificationCompat.notifyWithModernHelper(pushService, payload)
                true
            }.onFailure {
                logger.e("mock modern helper notify failure: ", it)
            }.getOrDefault(false)
        }
        try {
            invokeProcessMiPushMessage(pushService, container, payload)
            return true
        } catch (e: Exception) {
            if (shouldFallbackWithModernHelper(e)) {
                logger.w("mock fallback to modern helper due to PendingIntent flag crash")
                runCatching { SdkNotificationCompat.notifyWithModernHelper(pushService, payload) }
                    .onSuccess { return true }
                    .onFailure { fallbackError ->
                        logger.e("mock fallback notify failure: ", fallbackError)
                    }
            }
            logger.e("mock notification failure: ", e)
            return false
        }
    }

    @JvmStatic
    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        ClassNotFoundException::class
    )
    fun invokeProcessMiPushMessage(
        pushService: XMPushService,
        container: XmPushActionContainer,
        payload: ByteArray = XMPushUtils.packToBytes(container)
    ) {
        MockMessageRegistry.mark(container)
        logger.d(
            "invokeProcessMiPushMessage pkg=${container.packageName} action=${container.action} " +
                "isRequest=${container.isRequest} isEncrypt=${container.isEncryptAction}"
        )
        invokeProcessMiPushMessage(pushService, payload)
    }

    @JvmStatic
    @Throws(
        NoSuchMethodException::class,
        IllegalAccessException::class,
        InvocationTargetException::class,
        ClassNotFoundException::class
    )
    fun invokeProcessMiPushMessage(pushService: XMPushService, mockDecryptedContent: ByteArray) {
        val result = JavaCalls.callStaticMethodOrThrow<Any?>(
            MIPushEventProcessor::class.java.name,
            "processMIPushMessage",
            pushService,
            mockDecryptedContent,
            mockDecryptedContent.size.toLong()
        )
        logger.d(
            "processMIPushMessage invoked payloadSize=${mockDecryptedContent.size} " +
                "resultType=${result?.javaClass?.name ?: "void"} result=$result"
        )
    }

    private fun shouldFallbackWithModernHelper(error: Throwable?): Boolean {
        var cursor = error
        while (cursor != null) {
            val message = cursor.message ?: ""
            if (
                cursor is IllegalArgumentException &&
                message.contains("FLAG_IMMUTABLE", ignoreCase = true) &&
                message.contains("FLAG_MUTABLE", ignoreCase = true)
            ) {
                return true
            }
            cursor = cursor.cause
        }
        return false
    }
}
