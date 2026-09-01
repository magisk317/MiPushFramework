package io.github.magisk317.mipush.push.pipeline

import com.xiaomi.push.service.MIPushNotificationHelper
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.PushMetaInfo
import com.xiaomi.xmpush.thrift.XmPushActionContainer
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushActionUnRegistrationResult
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntime
import io.github.magisk317.mipush.runtime.store.kmp.RegisteredAppRegisteredType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MiPushRuntimeBridgeTest {

    @Test
    fun `only successful server registration results produce final state changes`() {
        val success = MIPushHelper.constructResponseContainer(
            "com.example.target",
            "app-id",
            registrationResult(errorCode = 0L),
            ActionType.Registration,
        )
        val failure = MIPushHelper.constructResponseContainer(
            "com.example.target",
            "app-id",
            registrationResult(errorCode = 1L),
            ActionType.Registration,
        )
        val missingSecret = MIPushHelper.constructResponseContainer(
            "com.example.target",
            "app-id",
            registrationResult(errorCode = 0L, secret = null),
            ActionType.Registration,
        )

        assertEquals(
            RegisteredAppRegisteredType.Registered,
            MiPushRuntimeBridge.resolveServerRegistrationState(success),
        )
        assertEquals(null, MiPushRuntimeBridge.resolveServerRegistrationState(failure))
        assertEquals(null, MiPushRuntimeBridge.resolveServerRegistrationState(missingSecret))
        assertTrue(MiPushRuntimeBridge.resolveRegistrationResultOutcome(success)!!.success)
        assertFalse(MiPushRuntimeBridge.resolveRegistrationResultOutcome(failure)!!.success)
        assertFalse(MiPushRuntimeBridge.resolveRegistrationResultOutcome(missingSecret)!!.success)
    }

    @Test
    fun `only successful server unregistration results produce final state changes`() {
        val success = MIPushHelper.constructResponseContainer(
            "com.example.target",
            "app-id",
            XmPushActionUnRegistrationResult("request-id", "app-id", 0L),
            ActionType.UnRegistration,
        )
        val failure = MIPushHelper.constructResponseContainer(
            "com.example.target",
            "app-id",
            XmPushActionUnRegistrationResult("request-id", "app-id", 1L),
            ActionType.UnRegistration,
        )

        assertEquals(
            RegisteredAppRegisteredType.Unregistered,
            MiPushRuntimeBridge.resolveServerRegistrationState(success),
        )
        assertEquals(null, MiPushRuntimeBridge.resolveServerRegistrationState(failure))
    }

    @Test
    fun `forged requests cannot be interpreted as verified final state`() {
        val forgedRegister = MIPushHelper.constructResponseContainer(
            "com.example.victim",
            "attacker-app-id",
            registrationResult(id = "forged", appId = "attacker-app-id", errorCode = 0L),
            ActionType.Registration,
        ).apply { setIsRequest(true) }
        val forgedUnregister = MIPushHelper.constructResponseContainer(
            "com.example.victim",
            "attacker-app-id",
            XmPushActionUnRegistrationResult("forged", "attacker-app-id", 0L),
            ActionType.UnRegistration,
        ).apply { setIsRequest(true) }

        assertEquals(null, MiPushRuntimeBridge.resolveServerRegistrationState(forgedRegister))
        assertEquals(null, MiPushRuntimeBridge.resolveServerRegistrationState(forgedUnregister))
    }

    @Test
    fun `mock replays cannot apply verified server registration state`() {
        assertFalse(MiPushRuntimeBridge.shouldApplyServerRegistrationState(isMockReplay = true))
        assertTrue(MiPushRuntimeBridge.shouldApplyServerRegistrationState(isMockReplay = false))
    }

    @Test
    fun `shouldProcessPayloadIdentity blocks duplicate notification payload within extended window`() {
        AndroidPushRuntime.clearStateForTests()

        assertTrue(
            MiPushRuntimeBridge.shouldProcessPayloadIdentity(
                packageName = "com.tencent.mobileqq",
                actionName = "SendMessage",
                messageId = "msg-1",
                source = "ClientEventDispatcher.notifyPacketArrival(blob)",
                isAck = false,
                isMockReplay = false,
                payloadSize = 128,
                androidUserId = 0,
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
                androidUserId = 0,
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

    private fun registrationResult(
        id: String = "request-id",
        appId: String = "app-id",
        errorCode: Long,
        secret: String? = "reg-secret",
    ) = XmPushActionRegistrationResult(id, appId, errorCode).apply {
        secret?.let(::setRegSecret)
    }
}
