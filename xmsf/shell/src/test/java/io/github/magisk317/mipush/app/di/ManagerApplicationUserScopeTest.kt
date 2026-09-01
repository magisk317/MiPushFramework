package io.github.magisk317.mipush.app.di

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.File

class ManagerApplicationUserScopeTest {
    @Test
    fun `transient application keeps the current Android user`() {
        val application = transientRegisteredApplication(
            packageName = "com.example.app",
            appName = "Example",
            userId = 999,
        )

        assertEquals(999, application.userId)
    }

    @Test
    fun `transient application rejects an unresolved user`() {
        assertThrows<IllegalArgumentException> {
            transientRegisteredApplication(
                packageName = "com.example.app",
                appName = "Example",
                userId = -1,
            )
        }
    }

    @Test
    fun `diagnostics adapter publishes the user used by its probes`() {
        val source = File("src/main/java/io/github/magisk317/mipush/app/di/ManagerApplicationAdapter.kt").readText()

        assertTrue(source.contains("val userId = Utils.requireValidUserId(Utils.myUserId())"))
        assertTrue(source.contains("userId = userId"))
    }
}
