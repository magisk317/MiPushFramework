package io.github.magisk317.mipush.push.pipeline

import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import io.github.magisk317.mipush.runtime.android.PushRuntime
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushRuntimeBridgeTest {

    @Test
    fun `shouldProcessPayloadIdentity blocks duplicate notification payload within extended window`() {
        PushRuntime.clearStateForTests()

        assertTrue(
            MiPushRuntimeBridge.shouldProcessPayloadIdentity(
                packageName = "com.tencent.mobileqq",
                actionName = "SendMessage",
                messageId = "msg-1",
                source = "ClientEventDispatcher.notifyPacketArrival(blob)",
                isAck = false,
                isMockReplay = false,
                payloadSize = 128,
            ),
        )
        assertFalse(
            MiPushRuntimeBridge.shouldProcessPayloadIdentity(
                packageName = "com.tencent.mobileqq",
                actionName = "SendMessage",
                messageId = "msg-1",
                source = "notification",
                isAck = false,
                isMockReplay = false,
                payloadSize = 128,
            ),
        )
    }

    @Test
    fun `stale package guard resolves miui target package`() {
        val container = XmPushActionContainer().apply {
            packageName = PushConstants.PUSH_SERVICE_PACKAGE_NAME
            metaInfo = PushMetaInfo().apply {
                extra = mutableMapOf(MIPushNotificationHelper.MIUI_PACKAGE_NAME to "com.example.removed")
            }
        }

        assertEquals("com.example.removed", StalePackagePushGuard.resolveTargetPackage(container))
    }
}
