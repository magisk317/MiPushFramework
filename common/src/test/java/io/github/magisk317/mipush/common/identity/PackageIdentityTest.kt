package io.github.magisk317.mipush.common.identity

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class PackageIdentityTest {
    @Test
    fun `identity is keyed by user and package`() {
        assertEquals(
            PackageIdentity(0, "com.example.app"),
            PackageIdentity(0, "com.example.app"),
        )
        assertEquals(
            PackageIdentity(999, "com.example.app"),
            PackageIdentity(999, "com.example.app"),
        )
    }

    @Test
    fun `identity rejects invalid scope`() {
        assertThrows(IllegalArgumentException::class.java) {
            PackageIdentity(-1, "com.example.app")
        }
        assertThrows(IllegalArgumentException::class.java) {
            PackageIdentity(0, " ")
        }
    }
}
