package com.xiaomi.push.service

import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionNotification
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
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
}
