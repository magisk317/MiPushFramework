package io.github.magisk317.mipush.common.utils.rom.miui

import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Test

class MiuiDexUtilsTest {

    @Test
    fun load_returnsFalseWhenDexAndLibraryAreMissing() {
        val result = MiuiDexUtils.load(
            dexPath = null,
            optimizedDirectory = null,
            librarySearchPath = null,
            classLoader = ClassLoader.getSystemClassLoader()
        )

        assertFalse(result)
    }

    @Test
    fun load_returnsFalseWhenLibraryPathHasNoContext() {
        val result = MiuiDexUtils.load(
            dexPath = null,
            optimizedDirectory = null,
            librarySearchPath = "/not/exist",
            classLoader = ClassLoader.getSystemClassLoader(),
            context = null
        )

        assertFalse(result)
    }
}
