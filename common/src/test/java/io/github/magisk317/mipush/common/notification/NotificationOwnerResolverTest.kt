package io.github.magisk317.mipush.common.notification

import android.os.Bundle
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.ExtendWith
import org.robolectric.annotation.Config
import tech.apter.junit.jupiter.robolectric.RobolectricExtension

// Keep Robolectric: this test relies on Android framework implementations indirectly;
// android.jar unit-test stubs throw "Method ... not mocked" without the extension.
@ExtendWith(RobolectricExtension::class)
@Config(sdk = [28])
class NotificationOwnerResolverTest {
    @Test
    fun `delegated notification resolves target package without changing notification semantics`() {
        val extras = Bundle().apply {
            putString(NotificationOwnerResolver.EXTRA_TARGET_PACKAGE, "com.example.target")
        }

        assertEquals(
            "com.example.target",
            NotificationOwnerResolver.resolve("com.xiaomi.xmsf", extras),
        )
    }

    @Test
    fun `native notification keeps posting package`() {
        assertEquals(
            "com.example.native",
            NotificationOwnerResolver.resolve("com.example.native", Bundle()),
        )
    }
}
