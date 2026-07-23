package io.github.magisk317.mipush.hook.systemui

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class HookSystemUIPluginTest {
    @Test
    fun `dispatches every hooker through one plugin observer`() {
        val calls = mutableListOf<String>()
        val plugin = HookSystemUIPlugin(
            "miui.systemui.plugin",
            ISystemUIPluginHooker { calls += "settings" },
            ISystemUIPluginHooker { calls += "authorization" },
            ISystemUIPluginHooker { calls += "focus-utils" },
        )

        plugin.dispatchHookers(ClassLoader.getSystemClassLoader())

        assertEquals(listOf("settings", "authorization", "focus-utils"), calls)
    }
}
