package com.xiaomi.mipush.sdk

import java.nio.file.Files
import java.nio.file.Path
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PushMessageProcessorSupportExtractionContractTest {
    @Test
    fun `processor keeps synchronous façades while support owns migrated operations`() {
        val processor = source("xmsf/shell/src/main/java/com/xiaomi/mipush/sdk/PushMessageProcessor.kt")
        val ack = source("xmsf/shell/src/main/java/com/xiaomi/mipush/sdk/PushMessageProcessorAckSupport.kt")
        val actionResult = source("xmsf/shell/src/main/java/com/xiaomi/mipush/sdk/PushMessageProcessorActionResultSupport.kt")
        val notificationAction = source("xmsf/shell/src/main/java/com/xiaomi/mipush/sdk/PushMessageProcessorNotificationActionSupport.kt")
        val intentFactory = source("xmsf/shell/src/main/java/com/xiaomi/mipush/sdk/PushMessageProcessorNotificationIntentFactory.kt")

        assertTrue(processor.contains("private fun ackMessage(container: XmPushActionContainer)"))
        assertTrue(processor.contains("private fun processRegistrationResult("))
        assertTrue(processor.contains("private fun processSubscriptionResult(result: XmPushActionSubscriptionResult)"))
        assertTrue(processor.contains("private fun processUnSubscriptionResult(result: XmPushActionUnSubscriptionResult)"))
        assertTrue(processor.contains("private fun processCommandResult(result: XmPushActionCommandResult, payload: ByteArray)"))
        assertTrue(processor.contains("private fun processAckNotification(notification: XmPushActionAckNotification)"))
        assertTrue(processor.contains("private fun processNotificationMessage("))
        assertTrue(processor.contains("actionResultSupport.processRegistrationResult"))
        assertTrue(processor.contains("notificationActionSupport.processNotificationMessage"))
        assertTrue(processor.contains("PerfMessageHelper.collectPerfData(sAppContext.packageName, sAppContext, body, ActionType.Notification, payload.size)"))
        assertTrue(actionResult.contains("internal class PushMessageProcessorActionResultSupport"))
        assertTrue(actionResult.contains("private val sAppContext: Context = context.applicationContext ?: context"))
        assertTrue(actionResult.contains("private val eventEmitter: PushMessageProcessorEventEmitter"))
        assertTrue(actionResult.contains("fun processRegistrationResult("))
        assertTrue(actionResult.contains("fun processSubscriptionResult("))
        assertTrue(actionResult.contains("fun processUnSubscriptionResult("))
        assertTrue(actionResult.contains("fun processCommandResult("))
        assertTrue(notificationAction.contains("internal class PushMessageProcessorNotificationActionSupport"))
        assertTrue(notificationAction.contains("private val sAppContext: Context = context.applicationContext ?: context"))
        assertTrue(notificationAction.contains("synchronized(OperatePushHelper::class.java)"))
        assertTrue(notificationAction.contains("helper.getRetryCount(id) < 10"))
        assertTrue(notificationAction.contains("ackSupport.sendAckNotification(notification)"))
        assertTrue(notificationAction.contains("fun processAckNotification("))
        assertTrue(notificationAction.contains("fun processEnableDisableAck("))
        assertTrue(notificationAction.contains("fun processNotificationMessage("))
        assertTrue(notificationAction.contains("fun processSendTokenAckNotification("))
        assertTrue(notificationAction.contains("fun processSingleTokenACK("))
        assertTrue(notificationAction.contains("fun processStatDataACK("))
        assertTrue(processor.contains("private fun ackMessage(sendMessage: XmPushActionSendMessage, container: XmPushActionContainer)"))
        assertTrue(processor.contains("private fun reportDecryptFail(container: XmPushActionContainer)"))
        assertTrue(processor.contains("private fun sendAckNotification(notification: XmPushActionNotification)"))
        assertTrue(processor.contains("PushMessageProcessorNotificationIntentFactory.getNotificationMessageIntent"))
        assertFalse(processor.contains("XmPushActionAckMessage().apply"))
        assertFalse(processor.contains("receive a message but decrypt failed. report now."))
        assertFalse(ack.substringBefore("fun ackMessage").contains("PushServiceClient"))
        assertTrue(ack.contains("sendMessage(ackMessage, ActionType.AckMessage, false, container.metaInfo)"))
        assertTrue(ack.contains("sendMessage(ackMessage, ActionType.AckMessage, metaInfo)"))
        assertTrue(ack.contains("\"client_ack_sent\""))
        assertTrue(ack.contains("\"clear_notification_ack_sent\""))
        assertTrue(intentFactory.contains("Intent.URI_INTENT_SCHEME"))
        assertTrue(intentFactory.contains("intent.setPackage(packageName)"))
        assertTrue(intentFactory.contains("ComponentName(packageName, map[\"class_name\"]!!)"))
        assertTrue(intentFactory.contains("intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)"))
        assertTrue(intentFactory.contains("PackageManager.MATCH_DEFAULT_ONLY"))

        val notificationIntent = source(
            "xmsf/shell/src/main/java/io/github/magisk317/mipush/service/runtime/" +
                "MyMIPushNotificationIntentSupport.kt",
        )
        assertTrue(notificationIntent.contains("ComponentName(container.packageName, BRIDGE_ACTIVITY_CLASS)"))
        assertTrue(notificationIntent.contains("applyPendingIntentIdentity(this, container.packageName"))
        assertTrue(notificationIntent.contains("logClickRoute(\"bridge_activity\""))
        assertTrue(notificationIntent.contains("logClickRoute(\"xmsf_service\""))
        assertTrue(notificationIntent.contains("PendingIntent.getActivity("))
    }

    private fun source(relativePath: String): String {
        val candidates = listOf(
            Path.of(relativePath),
            Path.of(relativePath.removePrefix("xmsf/shell/")),
        )
        var directory: Path? = Path.of("").toAbsolutePath()
        while (directory != null) {
            candidates.forEach { relative ->
                val candidate = directory.resolve(relative)
                if (Files.isRegularFile(candidate)) {
                    return Files.readString(candidate)
                }
            }
            directory = directory.parent
        }
        error("source is unavailable: $relativePath")
    }
}
