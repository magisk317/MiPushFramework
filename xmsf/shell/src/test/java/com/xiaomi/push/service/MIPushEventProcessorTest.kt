package com.xiaomi.push.service

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.NotificationType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionAckNotification
import com.xiaomi.xmpush.thrift.XmPushActionCommand
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

    @Test
    fun `clear push message with notifyId is consumed by the service and acked`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        val ackJobSlot = slot<XMPushServiceJob>()
        val notificationSlot = slot<XmPushActionNotification>()
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)
        every { observer.handleClearPushMessage(capture(notificationSlot)) } returns true

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply {
                setPayload(
                    clearPushMessagePayload(mapOf("notifyId" to "7")),
                    null,
                )
            },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        // The service consumes the control: no app-forward and stock-shaped clear ack.
        assertEquals("com.example.app", notificationSlot.captured.packageName)
        assertEquals(NotificationType.CancelPushMessage.value, notificationSlot.captured.type)
        assertEquals(
            MIPushClearPushMessageSupport.MatcherKind.NOTIFY_ID,
            MIPushClearPushMessageSupport.resolveMatcher(notificationSlot.captured.extra)?.kind,
        )
        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
        verify(exactly = 1) { pushAction.executeJob(capture(ackJobSlot)) }
        assertEquals(
            XMPushServiceJob.TYPE_SEND_MSG,
            ackJobSlot.captured.type,
        )
        assertEquals("send ack message for clear push message.", ackJobSlot.captured.getDesc())
        verify(exactly = 1) {
            observer.onNotificationEvent(
                "com.example.app",
                "clear_push_message_consumed",
                "MIPushEventProcessor.consumeClearPushMessageIfRequired",
            )
        }
    }

    @Test
    fun `clear push message with title and description routes to the observer and still acks on a miss`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        val ackJobSlot = slot<XMPushServiceJob>()
        val notificationSlot = slot<XmPushActionNotification>()
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)
        every { observer.handleClearPushMessage(capture(notificationSlot)) } returns false

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply {
                setPayload(
                    clearPushMessagePayload(mapOf("title" to "Hello", "description" to "World")),
                    null,
                )
            },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        assertEquals(
            MIPushClearPushMessageSupport.MatcherKind.TITLE_DESCRIPTION,
            MIPushClearPushMessageSupport.resolveMatcher(notificationSlot.captured.extra)?.kind,
        )
        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
        // Stock wc.a.a(): the no-match path still acks via e1.b(errorCode 0, result_code 3).
        verify(exactly = 1) { pushAction.executeJob(capture(ackJobSlot)) }
        assertEquals("send ack message for clear push message.", ackJobSlot.captured.getDesc())
    }

    @Test
    fun `clear push message without extras is consumed without observer call or ack`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)
        val notification = XmPushActionNotification().apply {
            setAppId("app-id")
            setPackageName("com.example.app")
            setId("clear-message-id")
            setType(NotificationType.CancelPushMessage.value)
            setRequireAck(false)
        }
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.generateRequestContainer(
                "com.example.app",
                "app-id",
                notification,
                ActionType.Notification,
            ),
        )

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply { setPayload(payload, null) },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        // Stock: a cancel control with null extra falls into the unrecognized-type
        // branch: consumed there, never acked (e1.b) and never forwarded.
        verify(exactly = 0) { observer.handleClearPushMessage(any()) }
        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
        verify(exactly = 0) { pushAction.executeJob(any()) }
        verify(exactly = 1) {
            observer.onNotificationEvent(
                "com.example.app",
                "clear_push_message_missing_extra",
                "MIPushEventProcessor.consumeClearPushMessageIfRequired",
            )
        }
    }

    @Test
    fun `non-clear notification keeps flowing through the normal dispatch untouched`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply { setPayload(samplePayload(), null) },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        verify(exactly = 0) { observer.handleClearPushMessage(any()) }
        verify(exactly = 1) { observer.processMIPushMessage(any(), any()) }
        verify(exactly = 0) { pushAction.executeJob(any()) }
    }

    @Test
    fun `callkit message routes to the observer and is consumed without ack on success`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)
        every { observer.isDuplicate("CALLKIT_MSG_com.example.app", "message-id") } returns false
        every { observer.handleCallKitMessage("com.example.app", any()) } returns "0"

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply { setPayload(hyperType3Payload(), null) },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        verify(exactly = 1) { observer.handleCallKitMessage("com.example.app", any()) }
        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
        verify(exactly = 0) { pushAction.executeJob(any()) }
        verify(exactly = 1) {
            observer.onNotificationEvent(
                "com.example.app",
                "callkit_message_routed",
                "MIPushEventProcessor.routeCallKitMessageIfRequired",
            )
        }
    }

    @Test
    fun `callkit handler failure sends the stock-shaped error ack`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        val ackJobSlot = slot<XMPushServiceJob>()
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)
        every { observer.isDuplicate("CALLKIT_MSG_com.example.app", "message-id") } returns false
        every { observer.handleCallKitMessage("com.example.app", any()) } returns "1"

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply { setPayload(hyperType3Payload(), null) },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        // Stock m0.g:722 l(..., "callkit_msg_handle_error", strA).
        verify(exactly = 1) { pushAction.executeJob(capture(ackJobSlot)) }
        assertEquals("send wrong message ack for message.", ackJobSlot.captured.getDesc())
        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
        verify(exactly = 1) {
            observer.onNotificationEvent(
                "com.example.app",
                "callkit_message_handle_error",
                "MIPushEventProcessor.routeCallKitMessageIfRequired",
            )
        }
    }

    @Test
    fun `duplicate callkit message is error acked without re-handling`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        val ackJobSlot = slot<XMPushServiceJob>()
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)
        every { observer.isDuplicate("CALLKIT_MSG_com.example.app", "message-id") } returns true

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply { setPayload(hyperType3Payload(), null) },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        verify(exactly = 0) { observer.handleCallKitMessage(any(), any()) }
        verify(exactly = 1) { pushAction.executeJob(capture(ackJobSlot)) }
        assertEquals("send wrong message ack for message.", ackJobSlot.captured.getDesc())
        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
    }

    @Test
    fun `setting app notification permission control is consumed and acked in stock shape`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val ackJobSlot = slot<XMPushServiceJob>()
        val notificationSlot = slot<XmPushActionNotification>()
        every { pushAction.runtimeObserver } returns observer
        // Stock u0.N only runs inside the real com.xiaomi.xmsf package (f.n gate).
        every { context.packageName } returns PushConstants.PUSH_SERVICE_PACKAGE_NAME
        every { pushAction.context } returns context
        every {
            observer.handleSettingAppNotificationPermission(capture(notificationSlot))
        } returns PushSettingAppNotificationPermissionResult(5L, "set switch error :not_ported")

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply {
                setPayload(
                    controlNotificationPayload(
                        type = NotificationType.SettingAppNotificationPermission.value,
                        extra = mapOf(
                            "permissionType" to "badge",
                            "permissionStatus" to "1",
                            "targetPackageName" to "com.example.target",
                            "targetAppId" to "288230376",
                        ),
                    ),
                    null,
                )
            },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        assertEquals(NotificationType.SettingAppNotificationPermission.value, notificationSlot.captured.type)
        assertEquals("badge", notificationSlot.captured.extra["permissionType"])
        verify(exactly = 1) { pushAction.executeJob(capture(ackJobSlot)) }
        assertEquals("send ack message for setting app notification permission.", ackJobSlot.captured.getDesc())
        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
    }

    @Test
    fun `setting app notification permission without a deciding handler does not ack`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        every { pushAction.runtimeObserver } returns observer
        every { context.packageName } returns PushConstants.PUSH_SERVICE_PACKAGE_NAME
        every { pushAction.context } returns context
        every { observer.handleSettingAppNotificationPermission(any()) } returns null

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply {
                setPayload(
                    controlNotificationPayload(
                        type = NotificationType.SettingAppNotificationPermission.value,
                        extra = mapOf("permissionType" to "badge"),
                    ),
                    null,
                )
            },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        // Stock u0.N listener-absent path: consumed, logged, no ack is invented.
        verify(exactly = 0) { pushAction.executeJob(any()) }
        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
        verify(exactly = 1) {
            observer.onNotificationEvent(
                "com.example.app",
                "setting_app_notification_permission_handler_absent",
                "MIPushEventProcessor.consumeSettingAppNotificationPermissionIfRequired",
            )
        }
    }

    @Test
    fun `inbound push data recover ack is silently consumed`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply { setPayload(controlAckPayload(NotificationType.PushDataForRecoverACK.value), null) },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
        verify(exactly = 0) { pushAction.executeJob(any()) }
        verify(exactly = 1) {
            observer.onNotificationEvent(
                "com.example.app",
                "push_data_recover_ack_consumed",
                "MIPushEventProcessor.consumeInboundControlAcksIfRequired",
            )
        }
    }

    @Test
    fun `inbound recover lbs subscription ack is silently consumed`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply { setPayload(controlAckPayload(NotificationType.RecoverLBSSubscriptionACK.value), null) },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
        verify(exactly = 0) { pushAction.executeJob(any()) }
        verify(exactly = 1) {
            observer.onNotificationEvent(
                "com.example.app",
                "recover_lbs_subscription_ack_consumed",
                "MIPushEventProcessor.consumeInboundControlAcksIfRequired",
            )
        }
    }

    @Test
    fun `lbs push command is observed, acked stock-style and still delivered`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        val ackJobSlot = slot<XMPushServiceJob>()
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply { setPayload(commandPayload("subscribe-lbs-push"), null) },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        verify(exactly = 1) { observer.onLbsPushCommand("com.example.app", "app-id", "subscribe-lbs-push") }
        // Stock acks the command via the standard d() AckMessage tail.
        verify(exactly = 1) { pushAction.executeJob(capture(ackJobSlot)) }
        assertEquals("send ack message for message.", ackJobSlot.captured.getDesc())
        // Stock still broadcasts the command to the app: normal dispatch continues.
        verify(exactly = 1) { observer.processMIPushMessage(any(), any()) }
    }

    @Test
    fun `unknown command names stay on the normal path untouched`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        every { pushAction.runtimeObserver } returns observer
        every { pushAction.context } returns mockk<Context>(relaxed = true)

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply { setPayload(commandPayload("some-other-command"), null) },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        verify(exactly = 0) { observer.onLbsPushCommand(any(), any(), any()) }
        verify(exactly = 0) { pushAction.executeJob(any()) }
        verify(exactly = 1) { observer.processMIPushMessage(any(), any()) }
    }

    @Test
    fun `awake system app probe answers with the stock-shaped response and consumes the probe`() {
        val observer = mockk<IPushRuntimeObserver>(relaxed = true)
        val pushAction = mockk<IPushServiceAction>(relaxed = true)
        val context = mockk<Context>(relaxed = true)
        val activityManager = mockk<android.app.ActivityManager>(relaxed = true)
        val ackJobSlot = slot<XMPushServiceJob>()
        every { pushAction.runtimeObserver } returns observer
        every { context.getSystemService(Context.ACTIVITY_SERVICE) } returns activityManager
        every { pushAction.context } returns context
        val container = MIPushHelper.generateRequestContainer(
            PushConstants.PUSH_SERVICE_PACKAGE_NAME,
            "app-id",
            XmPushActionNotification().apply {
                setAppId("app-id")
                setId("probe-body")
                setType("probe")
                setRequireAck(false)
            },
            ActionType.Notification,
        ).apply {
            metaInfo = PushMetaInfo().apply {
                id = "probe-id"
                putToExtra(PushConstants.EXTRA_PARAM_CHECK_ALIVE, "true")
                putToExtra(PushConstants.EXTRA_PARAM_AWAKE, "false")
            }
        }

        MIPushEventProcessor().processNewPacket(
            pushAction,
            Blob().apply { setPayload(XmPushThriftSerializeUtils.convertThriftObjectToBytes(container), null) },
            PushClientsManager.ClientLoginInfo().apply { security = "" },
        )

        verify(exactly = 1) { pushAction.executeJob(capture(ackJobSlot)) }
        assertEquals("send awake system app response.", ackJobSlot.captured.getDesc())
        verify(exactly = 0) { observer.processMIPushMessage(any(), any()) }
        // Relaxed ActivityManager reports no running process, so the stock "9" client-report
        // fallback (xc.d.h) lands on the observation channel.
        verify(exactly = 1) {
            observer.onNotificationEvent(
                PushConstants.PUSH_SERVICE_PACKAGE_NAME,
                "awake_probe_target_not_running",
                "MIPushEventProcessor.respondAwakeSystemAppProbeIfRequired",
            )
        }
    }

    private fun hyperType3Payload(): ByteArray {
        val container = MIPushHelper.generateRequestContainer(
            "com.example.app",
            "app-id",
            XmPushActionNotification().apply {
                setAppId("app-id")
                setPackageName("com.example.app")
                setId("body-id")
                setType("callkit-voip")
                setRequireAck(false)
            },
            ActionType.Notification,
        ).apply {
            metaInfo = PushMetaInfo().apply {
                id = "message-id"
                putToExtra("hyper_type", "3")
            }
        }
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(container)
    }

    private fun controlNotificationPayload(type: String, extra: Map<String, String>): ByteArray {
        val notification = XmPushActionNotification().apply {
            setAppId("app-id")
            setPackageName("com.example.app")
            setId("control-message-id")
            setType(type)
            setRequireAck(false)
            setExtra(extra.toMutableMap())
        }
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.generateRequestContainer(
                "com.example.app",
                "app-id",
                notification,
                ActionType.Notification,
            ),
        )
    }

    private fun controlAckPayload(type: String): ByteArray {
        val ack = XmPushActionAckNotification().apply {
            appId = "app-id"
            packageName = "com.example.app"
            id = "ack-message-id"
            this.type = type
        }
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.constructResponseContainer(
                "com.example.app",
                "app-id",
                ack,
                ActionType.Notification,
            ),
        )
    }

    private fun commandPayload(cmdName: String): ByteArray {
        val command = XmPushActionCommand().apply {
            appId = "app-id"
            id = "command-message-id"
            this.cmdName = cmdName
        }
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.generateRequestContainer(
                "com.example.app",
                "app-id",
                command,
                ActionType.Command,
            ),
        )
    }

    private fun clearPushMessagePayload(extra: Map<String, String>): ByteArray {
        val notification = XmPushActionNotification().apply {
            setAppId("app-id")
            setPackageName("com.example.app")
            setId("clear-message-id")
            setType(NotificationType.CancelPushMessage.value)
            setRequireAck(false)
            setExtra(extra.toMutableMap())
        }
        return XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.generateRequestContainer(
                "com.example.app",
                "app-id",
                notification,
                ActionType.Notification,
            ),
        )
    }
}
