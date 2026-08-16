package io.github.magisk317.mipush.manager

import io.github.magisk317.mipush.common.fakedevice.ZygiskConfig
import io.github.magisk317.mipush.common.manager.ZygiskConfigReadResult
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class SettingsManagerZygiskReadPolicyTest {
    @Test
    fun `unavailable config does not become an empty successful package set`() {
        val result = zygiskSpoofPackagesOrNull(ZygiskConfigReadResult.Unavailable("runtime_unavailable"))

        assertNull(result)
    }

    @Test
    fun `available config exposes enabled packages and package state`() {
        val config = ZygiskConfig.parse("profile=os4\ncom.example.enabled\n")
        val result = ZygiskConfigReadResult.Available(config)

        assertEquals(setOf("com.example.enabled"), zygiskSpoofPackagesOrNull(result))
        assertEquals(true, zygiskSpoofEnabledOrNull(result, "com.example.enabled"))
        assertEquals(false, zygiskSpoofEnabledOrNull(result, "com.example.other"))
    }
}
