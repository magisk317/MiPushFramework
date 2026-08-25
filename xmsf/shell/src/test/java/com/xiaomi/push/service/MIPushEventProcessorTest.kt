package com.xiaomi.push.service

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionSendMessage
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import com.xiaomi.slim.Blob
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.slot
import io.mockk.unmockkStatic
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.text.TextUtils

class MIPushEventProcessorTest {
    @BeforeEach
    fun setUpAndroidStatics() {
        mockkStatic(TextUtils::class)
        every { TextUtils.isEmpty(null) } returns true
        every { TextUtils.isEmpty(any<CharSequence>()) } answers { firstArg<CharSequence>().isEmpty() }
    }

    @AfterEach
    fun tearDownAndroidStatics() {
        unmockkStatic(TextUtils::class)
    }

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
        val context = mockk<Context>(relaxed = true)
        val packageManager = mockk<PackageManager>()
        every { context.packageManager } returns packageManager
        every { packageManager.getPackageInfo("com.example.missing", 0) } throws PackageManager.NameNotFoundException()
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
        val context = mockk<Context>(relaxed = true)
        val packageName = "com.example.installed"
        val packageManager = mockk<PackageManager>()
        every { context.packageManager } returns packageManager
        every { packageManager.getPackageInfo(any<String>(), any<Int>()) } throws PackageManager.NameNotFoundException()
        every { packageManager.getPackageInfo(eq(packageName), any<Int>()) } returns PackageInfo().apply { this.packageName = packageName }
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
        val ackJobSlot = slot<XMPushServiceJob>()
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)
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
        verify(exactly = 1) { pushAction.executeJob(capture(ackJobSlot)) }
        val ackJob = ackJobSlot.captured
        assertEquals(XMPushServiceJob.TYPE_SEND_MSG, ackJob.type)
        assertEquals(
            "send ack message for checking profileId error ack message.",
            ackJob.getDesc(),
        )

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
