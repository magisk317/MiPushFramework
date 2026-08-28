package io.github.magisk317.mipush.feature.main

import io.github.magisk317.mipush.manager.application.ManagerRuntimeEnvironmentSnapshot
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class MainActivityUtilsTest {
    @Test
    fun `runtime snapshot logs presence without identifiers`() {
        val snapshot = ManagerRuntimeEnvironmentSnapshot(
            isMiui = 1,
            imei = "123456789012345",
            macAddress = "aa:bb:cc:dd:ee:ff",
            xmppServerHost = "push.example.test",
        )

        val output = MainActivityUtils.safeRuntimeSnapshotLines(snapshot).joinToString("\n")

        assertTrue(output.contains("imeiPresent=true"))
        assertTrue(output.contains("macAddressPresent=true"))
        assertTrue(output.contains("xmppServerConfigured=true"))
        assertFalse(output.contains(snapshot.imei!!))
        assertFalse(output.contains(snapshot.macAddress!!))
        assertFalse(output.contains(snapshot.xmppServerHost))
    }
}
