package io.github.magisk317.mipush.bridge

import android.content.Context
import com.xiaomi.smack.Connection
import io.github.magisk317.mipush.runtime.core.PushRuntimeObservationSink
import io.github.magisk317.mipush.runtime.core.PushRuntimeRegistrationChannelObservationSink
import io.mockk.Called
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class MiPushRuntimeConnectionLifecycleAdapterTest {
    @Test
    fun `stale reconnect success does not publish reconnect telemetry`() {
        val observationSink = mockk<PushRuntimeObservationSink>(relaxed = true)
        val channelSink = mockk<PushRuntimeRegistrationChannelObservationSink>(relaxed = true)
        val state = MiPushRuntimeObserverState()
        val adapter = MiPushRuntimeConnectionLifecycleAdapter(
            context = mockk<Context>(relaxed = true),
            appContext = mockk<Context>(relaxed = true),
            state = state,
            runtimeObservationSink = observationSink,
            channelObservationSink = channelSink,
            publishConnectionStatus = {},
        )

        adapter.reconnectionSuccessful(mockk<Connection>(relaxed = true))

        verify { observationSink wasNot Called }
    }
}
