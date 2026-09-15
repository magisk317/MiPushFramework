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

    @Test
    fun `clear notification keeps the stock clicked button attribution surface`() {
        val source = resolveSource("XMPushServiceAppIntentDelegate.kt").readText()

        // Stock XMPushService.handleIntent:1550 reads ext_clicked_button off the
        // CLEAR_NOTIFICATION intent; the delegate must accept it and surface the
        // attribution through the observation channel.
        assertTrue(source.contains("intent.getIntExtra(PushConstants.EXTRA_CLICKED_BUTTON, -1)"))
        assertTrue(source.contains("clear_notification_clicked_button_"))
    }

    @Test
    fun `headsup clear and package lifecycle route to the runtime observer`() {
        val appSource = resolveSource("XMPushServiceAppIntentDelegate.kt").readText()
        val delegateSource = resolveSource("XMPushServiceIntentDelegate.kt").readText()

        assertTrue(appSource.contains("runtimeObserver.onClearHeadsupNotificationRequested(packageName)"))
        assertTrue(appSource.contains("runtimeObserver.onPackageAdded(packageName)"))
        assertTrue(appSource.contains("runtimeObserver.onPackageReplaced(packageName)"))
        assertTrue(
            delegateSource.contains("PushConstants.MIPUSH_ACTION_CLEAR_HEADSUPNOTIFICATION == action"),
        )
        assertTrue(delegateSource.contains("PushServiceConstants.ACTION_PACKAGE_ADD == action"))
        assertTrue(delegateSource.contains("PushServiceConstants.ACTION_PACKAGE_REPLACED == action"))
    }

    @Test
    fun `shell package receiver forwards stock add and replace actions`() {
        val source = resolveShellSource("io/github/magisk317/mipush/receiver/PkgUninstallReceiver.kt").readText()

        // Stock PkgActionsReceiver.java:96/109 forwards PACKAGE_ADD + PACKAGE_REPLACED
        // with the plain pkg_name extra.
        assertTrue(source.contains("PushServiceConstants.ACTION_PACKAGE_ADD"))
        assertTrue(source.contains("PushServiceConstants.ACTION_PACKAGE_REPLACED"))
        assertTrue(source.contains("PushServiceConstants.EXTRA_PKG_NAME"))
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

    private fun resolveShellSource(relativePath: String): File {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("../shell/src/main/java/$relativePath"),
        )
        return candidates.firstOrNull(File::isFile)
            ?: error("Cannot resolve $relativePath from ${File(".").absolutePath}")
    }
}
