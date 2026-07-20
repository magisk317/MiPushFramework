package com.xiaomi.push.service

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionAckMessage
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import com.xiaomi.slim.Blob
import com.xiaomi.smack.Connection
import com.xiaomi.smack.packet.Message
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class MIPushEventProcessorTest {
    @Test
    fun `buildContainer returns decoded thrift container`() {
        val payload = samplePayload()

        val container = MIPushEventProcessor.buildContainer(payload)

        assertNotNull(container)
        assertEquals("com.example.app", container?.packageName)
        assertEquals(ActionType.Notification, container?.action)
    }

    @Test
    fun `resolveMissingTargetPackage uses miui target package instead of service package`() {
        val context = RuntimeEnvironment.getApplication()
        val container = XmPushActionContainer().apply {
            packageName = PushConstants.PUSH_SERVICE_PACKAGE_NAME
            appid = "app-id"
            metaInfo = PushMetaInfo().apply {
                extra = mutableMapOf(MIPushNotificationHelper.MIUI_PACKAGE_NAME to "com.example.missing")
            }
        }

        assertEquals(
            "com.example.missing",
            MIPushEventProcessor.resolveMissingTargetPackage(context, container)
        )
    }

    @Test
    fun `resolveMissingTargetPackage ignores installed target package`() {
        val context = RuntimeEnvironment.getApplication()
        val packageName = "com.example.installed"
        shadowOf(context.packageManager).installPackage(
            android.content.pm.PackageInfo().apply { this.packageName = packageName }
        )
        val container = XmPushActionContainer().apply {
            this.packageName = packageName
            appid = "app-id"
            metaInfo = PushMetaInfo()
        }

        assertNull(MIPushEventProcessor.resolveMissingTargetPackage(context, container))
    }

    @Test
    fun `profile mismatch is dropped at decrypted runtime gate before normal processing`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        val connection = mockk<Connection>(relaxed = true)
        val account = MIPushAccount(
            account = "12345@xiaomi.com/resource",
            token = "token",
            security = "",
            appId = "app-id",
            appToken = "app-token",
            packageName = PushConstants.PUSH_SERVICE_PACKAGE_NAME,
            envType = 1,
        )
        val ackJob = slot<XMPushServiceJob>()
        val sentBlob = slot<Blob>()
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns RuntimeEnvironment.getApplication()
        every { pushAction.currentConnection } returns connection
        every { pushAction.executeJob(capture(ackJob)) } just Runs
        every { connection.isBinaryConnection() } returns true
        every { connection.send(capture(sentBlob)) } just Runs
        every { observer.loadAccount(any(), "MIPushHelper.sendPacket") } returns account
        every { observer.shouldAcceptProfile(any()) } returns false
        val payload = displaySendMessagePayload()
        val blob = Blob().apply { setPayload(payload, null) }

        MIPushEventProcessor().processNewPacket(
            pushAction,
            blob,
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        verify(exactly = 1) { observer.shouldAcceptProfile(any()) }
        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
        assertEquals(XMPushServiceJob.TYPE_SEND_MSG, ackJob.captured.type)
        assertEquals(
            "send ack message for checking profileId error ack message.",
            ackJob.captured.getDesc(),
        )

        ackJob.captured.process()

        val ackContainer = XmPushActionContainer()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(
            ackContainer,
            sentBlob.captured.getDecryptedPayload(account.security),
        )
        val ackMetaInfo = ackContainer.metaInfo
        assertNotNull(ackMetaInfo)
        assertEquals(ActionType.AckMessage, ackContainer.action)
        assertEquals("com.example.app", ackContainer.packageName)
        assertEquals("app-id", ackContainer.appid)
        assertEquals("message-id", ackMetaInfo.id)
        assertEquals(1_234_567_890L, ackMetaInfo.messageTs)
        assertEquals("profile-a", ackMetaInfo.extra["profileId"])
        assertEquals("1", ackMetaInfo.extra["Profile ID is missing"])
        assertEquals("profileId_missing", ackMetaInfo.extra[Message.MSG_TYPE_ERROR])
        assertEquals("Profile ID is missing", ackMetaInfo.extra["reason"])
        assertTrue(ackMetaInfo.extra[PushConstants.MESSAGE_ACK_TIME]?.toLongOrNull() != null)
        assertEquals(
            setOf("profileId", "Profile ID is missing", Message.MSG_TYPE_ERROR, "reason", PushConstants.MESSAGE_ACK_TIME),
            ackMetaInfo.extra.keys,
        )

        val ackMessage = XmPushActionAckMessage()
        XmPushThriftSerializeUtils.convertByteArrayToThriftObject(ackMessage, ackContainer.getPushAction())
        assertEquals("app-id", ackMessage.appId)
        assertEquals("message-id", ackMessage.id)
        assertEquals(1_234_567_890L, ackMessage.messageTs)
        verify(exactly = 1) { connection.send(any()) }
        verify(exactly = 1) {
            observer.onChannelEvent(
                "com.example.app",
                "service_profile_id_error_ack_sent",
                "MIPushAckDispatcher.sendProfileIdMismatchAck",
            )
        }
        verify(exactly = 1) {
            observer.onNotificationEvent(
                "com.example.app",
                "profile_id_mismatch_drop",
                "MIPushEventProcessor.processMIPushMessage",
            )
        }
    }

    private fun samplePayload(): ByteArray {
        val notification = XmPushActionNotification().apply {
            setAppId("app-id")
            setType("type")
            setId("message-id")
            setRequireAck(false)
        }
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.generateRequestContainer("com.example.app", "app-id", notification, ActionType.Notification),
        )
    }

    private fun displaySendMessagePayload(): ByteArray {
        val sendMessage = XmPushActionSendMessage().apply {
            setId("body-id")
            setAppId("app-id")
            setPackageName("com.example.app")
        }
        val container = MIPushHelper.generateRequestContainer(
            "com.example.app",
            "app-id",
            sendMessage,
            ActionType.SendMessage,
        ).apply {
            metaInfo = PushMetaInfo().apply {
                id = "message-id"
                messageTs = 1_234_567_890L
                title = "title"
                description = "content"
                passThrough = 0
                putToExtra("profileId", "profile-a")
            }
        }
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)
    }
}
