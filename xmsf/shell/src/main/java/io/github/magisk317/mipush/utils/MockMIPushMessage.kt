package io.github.magisk317.mipush.utils

import io.github.magisk317.mipush.common.utils.logD
import io.github.magisk317.mipush.common.utils.logE
import io.github.magisk317.xposed.logging.MagiskOtel
import io.github.magisk317.mipush.common.utils.logI
import io.github.magisk317.mipush.common.utils.logV
import io.github.magisk317.mipush.common.utils.logW

import io.github.magisk317.mipush.SdkNotificationCompat
import io.github.magisk317.mipush.manager.application.MockReplayOutcome
import io.github.magisk317.mipush.platform.support.XMPushUtils
import io.github.magisk317.mipush.push.pipeline.MessageIdentity
import io.github.magisk317.mipush.push.pipeline.MockMessageRegistry
import io.github.magisk317.mipush.runtime.PushRuntime
import com.xiaomi.channel.commonutils.reflect.JavaCalls
import com.xiaomi.push.service.MIPushEventProcessor
import com.xiaomi.push.service.PushConstants
import com.xiaomi.push.service.XMPushServiceCore
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicLong

object MockMIPushMessage {
    private val TAG = MockMIPushMessage::class.java.simpleName
    private val replaySequence = AtomicLong()

    @JvmStatic
    fun mockProcessMIPushMessage(
        pushService: XMPushServiceCore,
        container: XmPushActionContainer,
    ): MockReplayOutcome {
        val startedAt = System.nanoTime()
        val replayContainer = prepareReplayContainer(container)
        val payload = XMPushUtils.packToBytes(replayContainer)
        val messageId = MessageIdentity.fromContainer(replayContainer)
        observeReplayEvent(replayContainer, "mock_replay_prepare", "MockMIPushMessage.mockProcessMIPushMessage")
        logD(
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
            logD(
                "mockProcessMIPushMessage use modern helper pkg=${replayContainer.packageName} action=${replayContainer.action} " +
                    "messageId=$messageId"
            )
            return runCatching {
                val outcome = SdkNotificationCompat.notifyWithModernHelper(pushService, payload)
                observeReplayEvent(
                    replayContainer,
                    outcome.observationAction(),
                    "MockMIPushMessage.mockProcessMIPushMessage",
                )
                logD(
                    "mockProcessMIPushMessage modern helper completed pkg=${replayContainer.packageName} " +
                        "action=${replayContainer.action} messageId=$messageId outcome=$outcome"
                )
                outcome
            }.onFailure {
                observeReplayEvent(
                    replayContainer,
                    "mock_replay_modern_helper_failure",
                    "MockMIPushMessage.mockProcessMIPushMessage",
                )
                logE(
                    "mock modern helper notify failure pkg=${replayContainer.packageName} " +
                        "action=${replayContainer.action} messageId=$messageId payloadSize=${payload.size}",
                    it
                )
            }.getOrDefault(MockReplayOutcome.Failed).also { emitMockReplayOutcome(it, startedAt) }
        }
        try {
            invokeProcessMiPushMessage(pushService, replayContainer, payload)
            observeReplayEvent(
                replayContainer,
                "mock_replay_legacy_success",
                "MockMIPushMessage.mockProcessMIPushMessage",
            )
            logD(
                "mockProcessMIPushMessage legacy invoke completed pkg=${replayContainer.packageName} " +
                    "action=${replayContainer.action} messageId=$messageId"
            )
            emitMockReplayOutcome(MockReplayOutcome.Dispatched, startedAt)
            return MockReplayOutcome.Dispatched
        } catch (e: Exception) {
            if (shouldFallbackWithModernHelper(e)) {
                logW("mock fallback to modern helper due to PendingIntent flag crash")
                MockMessageRegistry.mark(replayContainer)
                observeReplayEvent(
                    replayContainer,
                    "mock_replay_modern_helper_fallback",
                    "MockMIPushMessage.mockProcessMIPushMessage",
                )
                runCatching { SdkNotificationCompat.notifyWithModernHelper(pushService, payload) }
                    .onSuccess { outcome ->
                        observeReplayEvent(
                            replayContainer,
                            outcome.observationAction(),
                            "MockMIPushMessage.mockProcessMIPushMessage",
                        )
                        logD(
                            "mockProcessMIPushMessage fallback modern helper completed pkg=${replayContainer.packageName} " +
                                "action=${replayContainer.action} messageId=$messageId outcome=$outcome"
                        )
                        emitMockReplayOutcome(outcome, startedAt)
                        return outcome
                    }
                    .onFailure { fallbackError ->
                        observeReplayEvent(
                            replayContainer,
                            "mock_replay_modern_helper_fallback_failure",
                            "MockMIPushMessage.mockProcessMIPushMessage",
                        )
                        logE(
                            "mock fallback notify failure pkg=${replayContainer.packageName} " +
                                "action=${replayContainer.action} messageId=$messageId payloadSize=${payload.size}",
                            fallbackError
                        )
                    }
            }
            observeReplayEvent(replayContainer, "mock_replay_failure", "MockMIPushMessage.mockProcessMIPushMessage")
            logE(
                "mock notification failure pkg=${replayContainer.packageName} action=${replayContainer.action} " +
                    "messageId=$messageId payloadSize=${payload.size}",
                e
            )
            emitMockReplayOutcome(MockReplayOutcome.Failed, startedAt)
            return MockReplayOutcome.Failed
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
        metaInfo.putToExtra(MockMessageRegistry.EXTRA_MOCK_REPLAY, "true")
        metaInfo.putToExtra(MockMessageRegistry.EXTRA_MOCK_REPLAY_SOURCE_ID, sourceId)
        logD("prepared mock replay sourceId=$sourceId replayId=$replayId")
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
        pushService: XMPushServiceCore,
        container: XmPushActionContainer,
        payload: ByteArray = XMPushUtils.packToBytes(container)
    ) {
        MockMessageRegistry.mark(container)
        logD(
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
    fun invokeProcessMiPushMessage(pushService: XMPushServiceCore, mockDecryptedContent: ByteArray) {
        val result = JavaCalls.callStaticMethodOrThrow(
            MIPushEventProcessor::class.java.name,
            "processMIPushMessage",
            pushService,
            mockDecryptedContent,
            mockDecryptedContent.size.toLong()
        )
        logD(
            "processMIPushMessage invoked payloadSize=${mockDecryptedContent.size} " +
                "resultType=${result.javaClass.name ?: "void"} result=$result"
        )
    }

    private fun emitMockReplay(result: String, statusOk: Boolean, reason: String, startedAt: Long) {
        val durationMs = ((System.nanoTime() - startedAt) / 1_000_000L).coerceAtLeast(0L)
        MagiskOtel.event(
            name = "push.dispatch",
            attributes = mapOf(
                "result" to result,
                "duration_ms" to durationMs.toString(),
                "process" to "main",
                "stage" to "mock_replay",
                "reason" to reason,
            ),
            statusOk = statusOk,
        )
    }

    private fun emitMockReplayOutcome(outcome: MockReplayOutcome, startedAt: Long) {
        when (outcome) {
            MockReplayOutcome.BlockedByPermission ->
                emitMockReplay(result = "skip", statusOk = true, reason = "blocked_by_permission", startedAt = startedAt)
            MockReplayOutcome.Dispatched ->
                emitMockReplay(result = "ok", statusOk = true, reason = "dispatched", startedAt = startedAt)
            MockReplayOutcome.Posted ->
                emitMockReplay(result = "ok", statusOk = true, reason = "posted", startedAt = startedAt)
            MockReplayOutcome.Failed ->
                emitMockReplay(result = "error", statusOk = false, reason = "failed", startedAt = startedAt)
        }
    }

    private fun observeReplayEvent(container: XmPushActionContainer, action: String, source: String) {
        PushRuntime.observeNotificationEvent(container.packageName, action, source)
    }

    internal fun MockReplayOutcome.observationAction(): String = when (this) {
        MockReplayOutcome.BlockedByPermission -> "mock_replay_blocked_by_permission"
        MockReplayOutcome.Dispatched -> "mock_replay_dispatched"
        MockReplayOutcome.Posted -> "mock_replay_posted"
        MockReplayOutcome.Failed -> "mock_replay_failed"
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
