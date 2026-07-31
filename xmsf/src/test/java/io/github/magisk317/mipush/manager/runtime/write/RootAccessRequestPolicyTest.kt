package io.github.magisk317.mipush.manager.runtime.write

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RootAccessRequestPolicyTest {
    @Test
    fun `refresh only query never requests authorization`() {
        var requestCount = 0

        val granted = resolveRootAccess(
            requestAuthorization = false,
            refreshAccess = { false },
            requestAccess = {
                requestCount += 1
                true
            },
        )

        assertFalse(granted)
        assertEquals(0, requestCount)
    }

    @Test
    fun `interactive query requests authorization when refresh fails`() {
        var requestCount = 0

        val granted = resolveRootAccess(
            requestAuthorization = true,
            refreshAccess = { false },
            requestAccess = {
                requestCount += 1
                true
            },
        )

        assertTrue(granted)
        assertEquals(1, requestCount)
    }

    @Test
    fun `interactive query skips authorization when refresh succeeds`() {
        var requestCount = 0

        val granted = resolveRootAccess(
            requestAuthorization = true,
            refreshAccess = { true },
            requestAccess = {
                requestCount += 1
                true
            },
        )

        assertTrue(granted)
        assertEquals(0, requestCount)
    }
}
