package io.github.magisk317.mipush.manager.xmsfdelivery

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XmsfSignatureVerifierTest {

    @Test
    fun `identical sets match`() {
        assertTrue(XmsfSignatureVerifier.matches(HOST, HOST))
    }

    @Test
    fun `order does not matter`() {
        assertTrue(XmsfSignatureVerifier.matches(HOST, HOST.reversed()))
    }

    @Test
    fun `different sets do not match`() {
        assertFalse(XmsfSignatureVerifier.matches(HOST, FOREIGN))
    }

    @Test
    fun `subset does not match`() {
        assertFalse(XmsfSignatureVerifier.matches(HOST, HOST.take(1)))
    }

    @Test
    fun `superset does not match`() {
        assertFalse(XmsfSignatureVerifier.matches(HOST, HOST + "extra-cert"))
    }

    @Test
    fun `empty host is never a match`() {
        assertFalse(XmsfSignatureVerifier.matches(emptyList(), emptyList()))
        assertFalse(XmsfSignatureVerifier.matches(emptyList(), FOREIGN))
    }

    @Test
    fun `empty candidate is never a match`() {
        assertFalse(XmsfSignatureVerifier.matches(HOST, emptyList()))
    }

    @Test
    fun `describeFailure walks the cause chain`() {
        val root = IllegalStateException("real reason")
        val middle = RuntimeException("wrapper", root)
        val top = IllegalArgumentException("top", middle)

        val described = top.describeFailure()

        assertTrue(described.contains("java.lang.IllegalArgumentException: top"), described)
        assertTrue(described.contains("java.lang.RuntimeException: wrapper"), described)
        assertTrue(described.contains("java.lang.IllegalStateException: real reason"), described)
    }

    @Test
    fun `describeFailure truncates very deep chains`() {
        var error: Throwable = IllegalStateException("root")
        repeat(50) { index -> error = RuntimeException("layer-$index", error) }

        val described = error.describeFailure()

        assertTrue(described.endsWith(" <- ..."), described)
        // MAX_CAUSE_DEPTH frames plus the truncation marker.
        assertEquals(9, described.split(" <- ").size)
    }

    private companion object {
        val HOST = listOf("host-cert-a", "host-cert-b")
        val FOREIGN = listOf("xiaomi-platform-cert")
    }
}
