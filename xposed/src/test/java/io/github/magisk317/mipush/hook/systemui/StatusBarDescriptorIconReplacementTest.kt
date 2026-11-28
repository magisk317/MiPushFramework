package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class StatusBarDescriptorIconReplacementTest {

    private class FakeDescriptor {
        @JvmField
        var icon: Any = Any()

        @JvmField
        var preloadedIcon: Any? = Any()
    }

    @Test
    fun `replacing descriptor icon clears stale preloaded icon`() {
        val descriptor = FakeDescriptor()
        val replacement = Any()

        replaceStatusBarDescriptorIcon(descriptor, replacement)

        assertSame(replacement, descriptor.icon)
        assertNull(descriptor.preloadedIcon)
    }
}
