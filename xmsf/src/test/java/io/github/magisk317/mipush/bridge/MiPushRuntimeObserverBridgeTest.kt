package io.github.magisk317.mipush.bridge

import android.app.Application
import com.xiaomi.push.service.IPendingPacketErrorNotifier
import com.xiaomi.push.service.PushConstants
import io.github.magisk317.mipush.runtime.android.PushRuntimePendingPacketStore
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
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
