package io.github.magisk317.mipush.platform.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BoundedShellContractTest {
    @Test
    fun `success requires zero exit without timeout or skip`() {
        assertTrue(BoundedShellResult(0).isSuccess)
        assertFalse(BoundedShellResult(0, timedOut = true).isSuccess)
        assertFalse(BoundedShellResult(0, skipped = true).isSuccess)
        assertFalse(BoundedShellResult(1).isSuccess)
    }

    @Test
    fun `text projections preserve line ordering`() {
        val result = BoundedShellResult(0, stdout = listOf("a", "b"), stderr = listOf("c", "d"))
        assertEquals("a\nb", result.stdoutText)
        assertEquals("c\nd", result.stderrText)
    }

    @Test
    fun `factory results remain fail closed`() {
        assertEquals(listOf("denied"), BoundedShellResult.skipped("denied").stderr)
        assertTrue(BoundedShellResult.skipped("denied").skipped)
        assertTrue(BoundedShellResult.timedOut().timedOut)
        assertEquals(listOf("broken"), BoundedShellResult.failed(IllegalStateException("broken")).stderr)
    }
}
