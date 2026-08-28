package io.github.magisk317.mipush.notification

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SweetNotificationCoordinatorStateOwnershipContractTest {
    @Test
    fun `package clear and expiry remove every persisted and in-memory state kind`() {
        val source = File("src/main/java/io/github/magisk317/mipush/notification/SweetNotificationCoordinator.kt")
            .readText()
        val clearPackage = source.substringAfter("fun clearPackageState(")
            .substringBefore("fun resolveCardContent")
        val prune = source.substringAfter("private fun pruneExpiredMilepostsLocked")
            .substringBefore("private fun readMilepost")

        assertTrue(clearPackage.contains("addAll(memoryMileposts.keys)"))
        assertTrue(clearPackage.contains("addAll(memoryClicked.keys)"))
        assertTrue(clearPackage.contains("addAll(memorySequence.keys)"))
        assertTrue(clearPackage.contains("keys.forEach { key -> clearStateLocked(context, key) }"))
        assertTrue(prune.contains("expired.forEach { key -> clearStateLocked(context, key) }"))
    }

    @Test
    fun `no retained sequence leaves a lower incoming sequence eligible`() {
        assertFalse(
            SweetNotificationCoordinator.decidePreflight(
                incomingStatus = "arriving",
                incomingSequence = 1L,
                previousSequence = 0L,
                milepostStatus = null,
                clickedStatus = null,
                activeNotification = false,
            ).suppress,
        )
    }
}
