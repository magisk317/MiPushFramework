package io.github.magisk317.mipush.notification

import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * Source contract for the per-app "suppress while disabled/suspended" gate. The real gate
 * depends on the runtime store and PackageManager, so the JVM test pins the wiring instead:
 * every bridge publish operation and the availability reader must consult the guard, and the
 * bridge must fail open when the store cannot be reached.
 */
class NotificationInactiveTargetGateContractTest {
    @Test
    fun `every bridge publish operation consults the inactive target guard`() {
        val source = readModuleSource("NotificationShellBridge.kt")

        assertEquals(
            2,
            Regex("shouldSuppressForInactiveTarget\\(packageName\\)").findAll(source).count(),
            "both notify() and postDetailed() must be gated",
        )
        assertTrue(
            source.contains("runCatching { RegisteredApplicationDb.shouldSuppressForInactiveApp(packageName) }"),
            "the bridge gate must fail open when the runtime store is unavailable",
        )
        assertTrue(source.contains("target_app_inactive"))
    }

    @Test
    fun `availability reader attributes disabled or suspended targets as notification-disabled`() {
        val source = readModuleSource("XmsfNotificationAvailabilityReader.kt")

        val installedCheck = source.indexOf("!Utils.isAppInstalled(packageName)")
        val inactiveCheck = source.indexOf("RegisteredApplicationDb.shouldSuppressForInactiveApp(packageName)")
        assertTrue(installedCheck >= 0)
        assertTrue(inactiveCheck > installedCheck, "inactive check belongs beside the installed check")
    }

    private fun readModuleSource(fileName: String): String =
        File("src/main/java/io/github/magisk317/mipush/notification/$fileName").readText()
}
