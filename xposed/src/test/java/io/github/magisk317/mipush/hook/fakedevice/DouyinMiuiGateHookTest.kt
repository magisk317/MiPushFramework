package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.hook.fakedevice.DouyinMiuiGateHook.matchesProviderGate
import java.lang.reflect.Method
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DouyinMiuiGateHookTest {
    object GateFixture {
        @JvmStatic
        fun isMiui(): Boolean = false

        @JvmStatic
        fun isMiui(ignoreCache: Boolean): Boolean = !ignoreCache

        @JvmStatic
        fun boxedIsMiui(): Boolean = true

        fun isMiuiInstance(): Boolean = true
    }

    object WrongNameFixture {
        @JvmStatic
        fun isMiuiX(): Boolean = true
    }

    private fun methodsOf(clazz: Class<*>): List<Method> = clazz.declaredMethods.filter { it.matchesProviderGate() }

    @Test
    fun `matches only the static zero-argument primitive boolean isMiui`() {
        val matched = methodsOf(GateFixture::class.java)
        assertTrue(matched.size == 1)
        assertTrue(matched.single().parameterCount == 0)
        assertFalse(
            methodsOf(GateFixture::class.java).any { it.name == "isMiui" && it.parameterCount == 1 },
        )
        assertFalse(
            methodsOf(GateFixture::class.java).any { !java.lang.reflect.Modifier.isStatic(it.modifiers) },
        )
    }

    @Test
    fun `wrong method name does not match the gate`() {
        assertTrue(methodsOf(WrongNameFixture::class.java).isEmpty())
    }
}
