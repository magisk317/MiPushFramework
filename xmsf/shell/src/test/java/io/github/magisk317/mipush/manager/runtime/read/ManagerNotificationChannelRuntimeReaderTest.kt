package io.github.magisk317.mipush.manager.runtime.read

import android.app.NotificationChannel
import android.app.NotificationChannelGroup
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ManagerNotificationChannelRuntimeReaderTest {
    @Test
    fun `reader preserves a valid secondary user`() {
        val page = reader(userId = 999).readPage(
            ManagerNotificationChannelReadQuery(
                packageName = "com.example.app",
                userId = 999,
            ),
        )

        assertEquals(999, page.userId)
        assertTrue(page.items.isEmpty())
        assertTrue(page.groups.isEmpty())
    }

    @Test
    fun `reader rejects an invalid provider user instead of falling back to primary`() {
        assertThrows(IllegalArgumentException::class.java) {
            reader(userId = -1).readPage(
                ManagerNotificationChannelReadQuery(packageName = "com.example.app", userId = -1),
            )
        }
    }

    @Test
    fun `default user provider is fail closed`() {
        val source = resolveSource().readText()

        assertFalse(source.contains("Utils.myUserId().coerceAtLeast(0)"))
        assertTrue(source.contains("private fun resolveManagerNotificationUserId(): Int = runCatching"))
        assertTrue(source.contains("takeIf { it >= 0 }"))
    }

    private fun reader(userId: Int) = ManagerNotificationChannelRuntimeReader(
        isHookedProvider = { false },
        channelProvider = { emptyList<NotificationChannel?>() },
        groupProvider = { emptyList<NotificationChannelGroup?>() },
        channelEnricher = { _, channels -> channels },
        userIdProvider = { userId },
    )

    private fun resolveSource(): File {
        val relativePath =
            "src/main/java/io/github/magisk317/mipush/manager/runtime/read/" +
                "ManagerNotificationChannelRuntimeReader.kt"
        return listOf(File(relativePath), File("xmsf/$relativePath"))
            .firstOrNull(File::isFile)
            ?: error("ManagerNotificationChannelRuntimeReader.kt not found")
    }
}
