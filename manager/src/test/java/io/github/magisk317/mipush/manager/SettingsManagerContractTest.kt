package io.github.magisk317.mipush.manager

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SettingsManagerContractTest {
    @Test
    fun `settings manager only exposes explicit constructor`() {
        val constructors = SettingsManager::class.java.declaredConstructors
        assertEquals(1, constructors.size)
        val ctor = constructors.single()
        assertTrue(ctor.parameterCount == 3)
    }
}
