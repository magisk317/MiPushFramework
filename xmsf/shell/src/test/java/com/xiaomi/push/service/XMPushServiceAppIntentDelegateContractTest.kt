package com.xiaomi.push.service

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class XMPushServiceAppIntentDelegateContractTest {
    @Test
    fun `raw packet account id keeps stock string parsing and primary fallback`() {
        val source = resolveSource("XMPushServicePacketDelegate.kt").readText()

        assertTrue(source.contains("intent.getStringExtra(PushConstants.EXTRA_USER_ID)?.toLongOrNull() ?: 0L"))
        assertFalse(source.contains("getLongExtra(PushConstants.EXTRA_USER_ID"))
    }

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
