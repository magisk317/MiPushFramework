package io.github.magisk317.mipush.runtime.data

import android.content.Context
import com.xiaomi.push.service.XMPushServiceCore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class EventRepositoryContractTest {

    @Test
    fun `mock replay bootstraps the private runtime core`() {
        val context: Context = RuntimeEnvironment.getApplication()

        val component = EventRepository.runtimeServiceIntent(context).component

        assertEquals(context.packageName, component?.packageName)
        assertEquals(XMPushServiceCore::class.java.name, component?.className)
    }
}
