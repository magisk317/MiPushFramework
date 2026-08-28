package com.xiaomi.mipush.sdk

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Characterizes the stock processor's relative side-effect order without adding a test seam to
 * its Context/AppInfoHolder/PushServiceClient pipeline. Refactors may move code only if they keep
 * these observable decode, ACK, gate, and dispatch boundaries in the same order.
 */
class PushMessageProcessorPipelineOrderContractTest {
    @Test
    fun `normal inbound pipeline decodes then acknowledges before gates and dispatch`() {
        val normalBranch = processorSource()
            .substringAfter("val fromNotification = intent.getBooleanExtra")

        assertOrder(
            normalBranch,
            "XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)",
            "metaInfo.putToExtra(PushConstants.MESSAGE_RECEIVE_TIME, receiveTime)",
            "normal inbound pipeline must decode before receive metadata",
        )
        assertOrder(
            normalBranch,
            "if (isHybridMsg(container))",
            "ackMessage(container)",
            "normal ACK must remain after hybrid ack-later classification",
        )
        assertOrder(
            normalBranch,
            "ackMessage(container)",
            "if (container.action == ActionType.SendMessage && !container.isEncryptAction)",
            "normal ACK must remain before unencrypted-message gating",
        )
        assertOrder(
            normalBranch,
            "if (!appInfoHolder.appRegistered() && container.action != ActionType.Registration)",
            "return processMessage(container, fromNotification, payload, messageId, eventMessageType)",
            "registration gating must precede normal message dispatch",
        )
    }

    @Test
    fun `arrived pipeline decodes then gates and dispatches without acknowledgement`() {
        val source = processorSource()
        val arrivedBranch = source.substring(
            source.indexOf("if (PushConstants.MIPUSH_ACTION_NEW_MESSAGE != action)"),
            source.indexOf("val fromNotification = intent.getBooleanExtra"),
        )

        assertOrder(
            arrivedBranch,
            "XmPushThriftSerializeUtils.convertByteArrayToThriftObject(container, payload)",
            "MIPushNotificationHelper.isBusinessMessage(container)",
            "arrived pipeline must decode before business-message gating",
        )
        assertOrder(
            arrivedBranch,
            "!appInfoHolder.appRegistered()",
            "return processMessage(container, payload)",
            "arrived registration gating must precede arrived dispatch",
        )
        assertFalse(
            arrivedBranch.contains("ackMessage("),
            "MESSAGE_ARRIVED must not send the normal inbound ACK",
        )
    }

    @Test
    fun `decrypt failure reports decrypt failure before generic failure telemetry`() {
        val source = processorSource()
        val decodePipeline = source.substring(
            source.indexOf("private fun processMessage(\n        container: XmPushActionContainer,\n        fromNotification"),
            source.indexOf("private fun processSendMessage("),
        )

        assertOrder(
            decodePipeline,
            "catch (e: DecryptException)",
            "reportDecryptFail(container)",
            "decrypt failure must be reported immediately in the decrypt catch path",
        )
        assertOrder(
            decodePipeline,
            "reportDecryptFail(container)",
            "ReportConstants.ERROR_DECRYPT_MSG_FAILED",
            "decrypt failure report must precede generic decrypt telemetry",
        )
    }

    private fun assertOrder(source: String, first: String, second: String, message: String) {
        val firstIndex = source.indexOf(first)
        val secondIndex = source.indexOf(second)
        assertTrue(firstIndex >= 0, "missing first order anchor: $first")
        assertTrue(secondIndex >= 0, "missing second order anchor: $second")
        assertTrue(firstIndex < secondIndex, message)
    }

    private fun processorSource(): String {
        val relativeCandidates = listOf(
            Path.of("src/main/java/com/xiaomi/mipush/sdk/PushMessageProcessor.kt"),
            Path.of("xmsf/shell/src/main/java/com/xiaomi/mipush/sdk/PushMessageProcessor.kt"),
        )
        var directory: Path? = Path.of("").toAbsolutePath()
        while (directory != null) {
            relativeCandidates.forEach { relative ->
                val candidate = directory.resolve(relative)
                if (Files.isRegularFile(candidate)) {
                    return Files.readString(candidate)
                }
            }
            directory = directory.parent
        }
        error("PushMessageProcessor source is unavailable to the stock order contract test")
    }
}
