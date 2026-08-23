package io.github.magisk317.mipush.hook.amap

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class AmapNavigationLiveViewPayloadTest {
    @Test
    fun `connect success callback payload remains compatible`() {
        assertEquals(
            """{"code":1,"message":{"msg":"connect_success","manufacturer":"XIAOMI","deviceName":"XiaomiFocus"},"displayName":"","deviceType":""}""",
            AmapNavigationLiveViewPayload.connectSuccess().toString(),
        )
    }
}
