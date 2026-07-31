package io.github.magisk317.mipush.app.di

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DualAppUserPolicyTest {
    @Test
    fun `dual app lifecycle is owned by primary user`() {
        assertTrue(canManageDualAppFromUser(0))
        assertFalse(canManageDualAppFromUser(999))
        assertFalse(canManageDualAppFromUser(10))
    }
}
