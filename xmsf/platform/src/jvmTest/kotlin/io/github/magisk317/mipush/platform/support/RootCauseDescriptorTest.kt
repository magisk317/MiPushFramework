package io.github.magisk317.mipush.platform.support

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class RootCauseDescriptorTest {
    @Test
    fun `describes the throwable itself when it has no cause`() {
        val throwable = IllegalStateException("wrapper only")

        assertEquals("cause=java.lang.IllegalStateException", rootCauseDescriptor(throwable))
    }

    @Test
    fun `walks the cause chain to the root cause`() {
        val throwable = RuntimeException(
            "outer",
            IllegalStateException("middle", IllegalArgumentException("root")),
        )

        assertEquals("cause=java.lang.IllegalArgumentException", rootCauseDescriptor(throwable))
    }

    @Test
    fun `names the class whose static initialization failed`() {
        val throwable = ExceptionInInitializerError(
            NoClassDefFoundError("Could not initialize class com.example.Broken"),
        )

        assertEquals(
            "cause=java.lang.NoClassDefFoundError failedInit=com.example.Broken",
            rootCauseDescriptor(throwable),
        )
    }

    @Test
    fun `keeps the innermost init failure across a masked chain`() {
        // The production shape: the sanitizer masks Throwable.message, so the EIIE wrapper
        // carries no usable text and only the root cause names the broken class.
        val throwable = RuntimeException(
            "payload[len=23 hash=deadbeef]",
            ExceptionInInitializerError(
                NoClassDefFoundError("Could not initialize class com.topjohnwu.superuser.Shell"),
            ),
        )

        assertEquals(
            "cause=java.lang.NoClassDefFoundError failedInit=com.topjohnwu.superuser.Shell",
            rootCauseDescriptor(throwable),
        )
    }

    @Test
    fun `no class def found error without the init message carries no failedInit`() {
        val throwable = NoClassDefFoundError("com.example.Missing")

        assertEquals("cause=java.lang.NoClassDefFoundError", rootCauseDescriptor(throwable))
    }

    @Test
    fun `non no class def error never leaks message text into the descriptor`() {
        // The message prefix alone must not qualify: the descriptor only re-exposes class
        // names, so an arbitrary throwable repeating the prefix contributes nothing.
        val throwable = IllegalStateException("Could not initialize class com.example.Broken")

        assertEquals("cause=java.lang.IllegalStateException", rootCauseDescriptor(throwable))
    }

    @Test
    fun `empty class name after the init message contributes no failedInit`() {
        val throwable = NoClassDefFoundError("Could not initialize class ")

        assertEquals("cause=java.lang.NoClassDefFoundError", rootCauseDescriptor(throwable))
    }

    @Test
    fun `self referencing cause chain terminates at the throwable itself`() {
        val throwable = SelfCaused()

        assertEquals(
            "cause=io.github.magisk317.mipush.platform.support.RootCauseDescriptorTest.SelfCaused",
            rootCauseDescriptor(throwable),
        )
    }

    private class SelfCaused : IllegalStateException("loop") {
        override val cause: Throwable? get() = this
    }
}
