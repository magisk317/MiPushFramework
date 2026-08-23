package io.github.magisk317.mipush.common.notification

import android.os.Bundle
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class NotificationOwnerResolverTest {
    @Test
    fun `delegated notification resolves target package without changing notification semantics`() {
        val extras = mockk<Bundle>(relaxed = true)
        every { extras.getString(NotificationOwnerResolver.EXTRA_TARGET_PACKAGE) } returns "com.example.target"

        assertEquals(
            "com.example.target",
            NotificationOwnerResolver.resolve("com.xiaomi.xmsf", extras),
        )
    }

    @Test
    fun `native notification keeps posting package`() {
        val extras = mockk<Bundle>(relaxed = true)

        assertEquals(
            "com.example.native",
            NotificationOwnerResolver.resolve("com.example.native", extras),
        )
    }
}
