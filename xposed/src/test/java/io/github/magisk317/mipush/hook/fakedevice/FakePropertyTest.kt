package io.github.magisk317.mipush.hook.fakedevice

import android.os.Build
import io.github.magisk317.mipush.hook.XLog
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FakePropertyTest {
    @BeforeEach
    fun setUpLogging() {
        mockkObject(XLog)
        every { XLog.d(any(), any()) } just Runs
        every { XLog.i(any(), any()) } just Runs
        every { XLog.w(any(), any()) } just Runs
    }

    @AfterEach
    fun tearDownLogging() {
        unmockkObject(XLog)
    }

    @Test
    fun `safe property application records an unknown field failure and continues`() {
        val unknownKey = "ro.build.hw_emui_api_level"
        val attemptedKeys = mutableListOf<String>()
        val failedKeys = mutableListOf<String>()

        assertDoesNotThrow {
            fakePropertiesSafely(
                properties = linkedMapOf(
                    unknownKey to "",
                    "ro.product.brand" to "Xiaomi",
                ),
                propertyWriter = { (key, _) ->
                    attemptedKeys += key
                    if (key == unknownKey) {
                        throw NoSuchFieldException(key)
                    }
                },
                failureReporter = { key, _ -> failedKeys += key },
            )
        }

        assertEquals(listOf(unknownKey, "ro.product.brand"), attemptedKeys)
        assertEquals(listOf(unknownKey), failedKeys)
    }

    @Test
    fun `all built in properties execute without propagating unknown fields`() {
        assertDoesNotThrow { fakeAllBuildInProperties() }
        assertFalse(
            mapOf("ro.build.hw_emui_api_level" to "").buildFieldOverrides()
                .any { it.fieldName == "ro.build.hw_emui_api_level" },
        )
    }

    @Test
    fun `build field overrides only include known Build fields`() {
        val overrides = mapOf(
            "ro.build.hw_emui_api_level" to "",
            "ro.product.brand" to "Xiaomi",
            "ro.build.version.release" to "12",
        ).buildFieldOverrides()

        assertEquals(2, overrides.size)
        assertTrue(
            overrides.any {
                it.targetClass == Build::class.java &&
                    it.fieldName == "BRAND" &&
                    it.value == "Xiaomi"
            },
        )
        assertTrue(
            overrides.any {
                it.targetClass == Build.VERSION::class.java &&
                    it.fieldName == "RELEASE" &&
                    it.value == "12"
            },
        )
        assertFalse(overrides.any { it.fieldName == "ro.build.hw_emui_api_level" })
    }

    @Test
    fun `build field overrides include push identity fields from real Xiaomi props`() {
        val overrides = mapOf(
            "ro.product.board" to "picasso",
            "ro.product.cpu.abi" to "arm64-v8a",
            "ro.product.cpu.abi2" to "armeabi-v7a",
            "ro.build.host" to "miui-build",
            "ro.build.tags" to "release-keys",
            "ro.build.type" to "user",
            "ro.build.version.incremental" to "V13.0.5.0.SGICNXM",
        ).buildFieldOverrides()

        assertEquals(7, overrides.size)
        assertTrue(overrides.any { it.targetClass == Build::class.java && it.fieldName == "BOARD" && it.value == "picasso" })
        assertTrue(overrides.any { it.targetClass == Build::class.java && it.fieldName == "CPU_ABI" && it.value == "arm64-v8a" })
        assertTrue(overrides.any { it.targetClass == Build::class.java && it.fieldName == "CPU_ABI2" && it.value == "armeabi-v7a" })
        assertTrue(overrides.any { it.targetClass == Build::class.java && it.fieldName == "HOST" && it.value == "miui-build" })
        assertTrue(overrides.any { it.targetClass == Build::class.java && it.fieldName == "TAGS" && it.value == "release-keys" })
        assertTrue(overrides.any { it.targetClass == Build::class.java && it.fieldName == "TYPE" && it.value == "user" })
        assertTrue(
            overrides.any {
                it.targetClass == Build.VERSION::class.java &&
                    it.fieldName == "INCREMENTAL" &&
                    it.value == "V13.0.5.0.SGICNXM"
            },
        )
    }
}
