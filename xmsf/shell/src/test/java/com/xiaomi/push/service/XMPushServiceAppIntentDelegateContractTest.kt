package com.xiaomi.push.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class XMPushServiceAppIntentDelegateContractTest {
    @Test
    fun `application requests do not write final registration state`() {
        val source = resolveSource("XMPushServiceAppIntentDelegate.kt").readText()
        val packetSource = resolveSource("XMPushServicePacketDelegate.kt").readText()

        assertFalse(source.contains("removeUnRegisteredPkg"))
        assertFalse(source.contains("addUnRegisteredPkg"))
        assertFalse(source.contains("PushRegistrationState.Unregistered"))
        assertFalse(packetSource.contains("rememberRegisteredPackage"))
        assertTrue(source.contains("runtimeObserver.onPackageDataCleared(packageName)"))
    }

    private fun resolveSource(fileName: String): File {
        val candidates = listOf(
            File("vendor/src/main/java/com/xiaomi/push/service/$fileName"),
            File("../vendor/src/main/java/com/xiaomi/push/service/$fileName"),
            File("../../vendor/src/main/java/com/xiaomi/push/service/$fileName"),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Cannot resolve $fileName from ${File(".").absolutePath}")
    }
}
