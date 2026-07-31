package io.github.magisk317.mipush.bridge

import android.app.Application
import android.content.Intent
import com.xiaomi.mipush.sdk.AppInfoHolder
import com.xiaomi.mipush.sdk.MiPushCommandMessage
import com.xiaomi.push.service.MIPushHelper
import com.xiaomi.push.service.IPendingPacketErrorNotifier
import com.xiaomi.push.service.PushConstants
import com.xiaomi.xmpush.thrift.ActionType
import com.xiaomi.xmpush.thrift.XmPushActionRegistrationResult
import com.xiaomi.xmpush.thrift.XmPushThriftSerializeUtils
import io.github.magisk317.mipush.runtime.PushRuntime
import io.github.magisk317.mipush.runtime.android.AndroidPushRuntime
import io.github.magisk317.mipush.runtime.android.PushRuntimePendingPacketStore
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28], application = Application::class)
class MiPushRuntimeObserverBridgeTest {
    @Test
    fun `registration response is processed and stored for xmsf itself`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val appInfo = AppInfoHolder.getInstance(context)
        val requestId = "request-id"
        AndroidPushRuntime.clearStateForTests()
        appInfo.clear()
        appInfo.putAppIDAndToken("app-id", "app-token", "resource")
        appInfo.appRegRequestId = requestId
        val result = XmPushActionRegistrationResult(requestId, "app-id", 0L).apply {
            setRegId("reg-id")
            setRegSecret("reg-secret")
            setRegion("China")
        }
        val payload = XmPushThriftSerializeUtils.convertThriftObjectToBytes(
            MIPushHelper.constructResponseContainer(
                context.packageName,
                "app-id",
                result,
                ActionType.Registration,
            ),
        )
        val intent = Intent(PushConstants.MIPUSH_ACTION_NEW_MESSAGE).apply {
            putExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD, payload)
        }

        val processed = MiPushRuntimeObserverBridge(context).processMIPushIntent(intent)

        assertNotNull(processed)
        assertEquals("register", (processed as MiPushCommandMessage).command)
        assertEquals("reg-id", appInfo.regID)
        assertEquals("reg-secret", appInfo.regSecret)
        assertEquals("China", appInfo.appRegion)
        assertEquals(null, appInfo.appRegRequestId)
        assertEquals(1, PushRuntime.snapshot().registeredPackageCount)
    }

    @Test
    fun `registration error targets each pending package without xmsf fallback`() {
        val context: Application = RuntimeEnvironment.getApplication()
        val packageName = "com.example.target"
        val payload = byteArrayOf(1, 2, 3)
        var fallbackCalls = 0
        PushRuntimePendingPacketStore.clearForTests()
        shadowOf(context).clearBroadcastIntents()
        PushRuntimePendingPacketStore.cacheRegistrationRequest(packageName, payload)

        MiPushRuntimeObserverBridge(context).notifyRegisterError(
            errorCode = 70000002,
            errorMessage = "no account",
            notifier = object : IPendingPacketErrorNotifier {
                override fun notifyError(errorCode: Int, errorMessage: String) {
                    fallbackCalls += 1
                }
            },
        )

        val errors = shadowOf(context).broadcastIntents
            .filter { it.action == PushConstants.MIPUSH_ACTION_ERROR }
        assertEquals(0, fallbackCalls)
        assertEquals(1, errors.size)
        assertEquals(packageName, errors.single().`package`)
        assertArrayEquals(payload, errors.single().getByteArrayExtra(PushConstants.MIPUSH_EXTRA_PAYLOAD))
        assertFalse(errors.any { it.`package` == context.packageName })
    }
}
