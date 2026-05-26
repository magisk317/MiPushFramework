package io.github.magisk317.mipush.utils

import io.github.aakira.napier.Napier
import io.github.aakira.napier.DebugAntilog
import io.github.magisk317.mipush.SdkNotificationCompat
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.push.pipeline.MessageIdentity
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import io.github.magisk317.mipush.runtime.PushRuntime
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.XMPushService
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicLong

object MockMIPushMessage {
    private val TAG = MockMIPushMessage::class.java.simpleName
    private const val EXTRA_MOCK_REPLAY = "mipush_mock_replay"
    private const val EXTRA_MOCK_REPLAY_SOURCE_ID = "mipush_mock_replay_source_id"
    private val replaySequence = AtomicLong()
    private val logger = object {
        fun d(msg: String) = Napier.d(msg, tag = TAG)
        fun w(msg: String) = Napier.w(msg, tag = TAG)
        fun e(msg: String, t: Throwable? = null) = Napier.e(msg, t, tag = TAG)
    }

    @JvmStatic
    fun mockProcessMIPushMessage(pushService: XMPushService, container: XmPushActionContainer): Boolean {
        val replayContainer = prepareReplayContainer(container)
        val payload = XMPushUtils.packToBytes(replayContainer)
        val messageId = MessageIdentity.fromContainer(replayContainer)
        observeReplayEvent(replayContainer, "mock_replay_prepare", "MockMIPushMessage.mockProcessMIPushMessage")
        logger.d(
            "mockProcessMIPushMessage start pkg=${replayContainer.packageName} action=${replayContainer.action} " +
                "messageId=$messageId payloadSize=${payload.size} isRequest=${replayContainer.isRequest} " +
                "isEncrypt=${replayContainer.isEncryptAction}"
        )
        if (SdkNotificationCompat.shouldUseModernHelper(payload)) {
            MockMessageRegistry.mark(replayContainer)
            observeReplayEvent(
                replayContainer,
                "mock_replay_modern_helper_start",
                "MockMIPushMessage.mockProcessMIPushMessage",
            )
            logger.d(
                "mockProcessMIPushMessage use modern helper pkg=${replayContainer.packageName} action=${replayContainer.action} " +
                    "messageId=$messageId"
            )
            return runCatching {
                SdkNotificationCompat.notifyWithModernHelper(pushService, payload)
                observeReplayEvent(
                    replayContainer,
                    "mock_replay_modern_helper_success",
                    "MockMIPushMessage.mockProcessMIPushMessage",
                )
                logger.d(
                    "mockProcessMIPushMessage modern helper completed pkg=${replayContainer.packageName} " +
                        "action=${replayContainer.action} messageId=$messageId"
                )
                true
            }.onFailure {
                observeReplayEvent(
                    replayContainer,
                    "mock_replay_modern_helper_failure",
                    "MockMIPushMessage.mockProcessMIPushMessage",
                )
                logger.e(
                    "mock modern helper notify failure pkg=${replayContainer.packageName} " +
                        "action=${replayContainer.action} messageId=$messageId payloadSize=${payload.size}",
                    it
                )
            }.getOrDefault(false)
        }
        try {
            invokeProcessMiPushMessage(pushService, replayContainer, payload)
            observeReplayEvent(
                replayContainer,
                "mock_replay_legacy_success",
                "MockMIPushMessage.mockProcessMIPushMessage",
            )
            logger.d(
                "mockProcessMIPushMessage legacy invoke completed pkg=${replayContainer.packageName} " +
                    "action=${replayContainer.action} messageId=$messageId"
            )
            return true
        } catch (e: Exception) {
            if (shouldFallbackWithModernHelper(e)) {
                logger.w("mock fallback to modern helper due to PendingIntent flag crash")
                MockMessageRegistry.mark(replayContainer)
                observeReplayEvent(
                    replayContainer,
                    "mock_replay_modern_helper_fallback",
                    "MockMIPushMessage.mockProcessMIPushMessage",
                )
                runCatching { SdkNotificationCompat.notifyWithModernHelper(pushService, payload) }
                    .onSuccess {
                        observeReplayEvent(
                            replayContainer,
                            "mock_replay_modern_helper_fallback_success",
                            "MockMIPushMessage.mockProcessMIPushMessage",
                        )
                        logger.d(
                            "mockProcessMIPushMessage fallback modern helper completed pkg=${replayContainer.packageName} " +
                                "action=${replayContainer.action} messageId=$messageId"
                        )
                        return true
                    }
                    .onFailure { fallbackError ->
                        observeReplayEvent(
                            replayContainer,
                            "mock_replay_modern_helper_fallback_failure",
                            "MockMIPushMessage.mockProcessMIPushMessage",
                        )
                        logger.e(
                            "mock fallback notify failure pkg=${replayContainer.packageName} " +
                                "action=${replayContainer.action} messageId=$messageId payloadSize=${payload.size}",
                            fallbackError
                        )
                    }
            }
            observeReplayEvent(replayContainer, "mock_replay_failure", "MockMIPushMessage.mockProcessMIPushMessage")
            logger.e(
                "mock notification failure pkg=${replayContainer.packageName} action=${replayContainer.action} " +
                    "messageId=$messageId payloadSize=${payload.size}",
                e
            )
            return false
        }
    }

    internal fun prepareReplayContainer(
        container: XmPushActionContainer,
        nowMs: Long = System.currentTimeMillis(),
        sequence: Long = replaySequence.incrementAndGet(),
    ): XmPushActionContainer {
        val metaInfo = container.metaInfo ?: return container
        val sourceId = MessageIdentity.fromContainer(container)
            ?: metaInfo.id?.takeIf { it.isNotBlank() }
            ?: "message"
        val replayId = "$sourceId#mock-$nowMs-$sequence"
        metaInfo.id = replayId
        metaInfo.setMessageTs(nowMs)
        metaInfo.putToExtra(PushConstants.EXTRA_JOB_KEY, replayId)
        metaInfo.putToExtra(EXTRA_MOCK_REPLAY, "true")
        metaInfo.putToExtra(EXTRA_MOCK_REPLAY_SOURCE_ID, sourceId)
        logger.d("prepared mock replay sourceId=$sourceId replayId=$replayId")
        return container
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
        val result = JavaCalls.callStaticMethodOrThrow(
            MIPushEventProcessor::class.java.name,
            "processMIPushMessage",
            pushService,
            mockDecryptedContent,
            mockDecryptedContent.size.toLong()
        )
        logger.d(
            "processMIPushMessage invoked payloadSize=${mockDecryptedContent.size} " +
                "resultType=${result.javaClass.name ?: "void"} result=$result"
        )
    }

    private fun observeReplayEvent(container: XmPushActionContainer, action: String, source: String) {
        PushRuntime.observeNotificationEvent(container.packageName, action, source)
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
