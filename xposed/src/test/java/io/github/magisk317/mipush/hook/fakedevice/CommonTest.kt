package io.github.magisk317.mipush.hook.fakedevice

import io.github.magisk317.mipush.hook.XLog
import io.github.magisk317.xposed.LoadParam
import io.mockk.Runs
import io.mockk.every
import io.mockk.just
import io.mockk.mockkObject
import io.mockk.unmockkObject
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CommonTest {
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
    fun `a failed step does not prevent the remaining Common steps`() {
        val steps = mutableListOf<String>()
        val common = RecordingCommon(steps = steps, failingSteps = setOf("properties"))

        val result = common.fake(loadParam())

        assertTrue(result)
        assertEquals(listOf("properties", "ali_bridge", "class_bridge"), steps)
        verify(exactly = 1) {
            XLog.w(
                "Common",
                "build properties failed: IllegalStateException: forced failure: properties",
            )
        }
    }

    @Test
    fun `Common reports failure when every step fails`() {
        val steps = mutableListOf<String>()
        val common = RecordingCommon(
            steps = steps,
            failingSteps = setOf("properties", "ali_bridge", "class_bridge"),
        )

        val result = common.fake(loadParam())

        assertFalse(result)
        assertEquals(listOf("properties", "ali_bridge", "class_bridge"), steps)
        verify(exactly = 1) { XLog.w("Common", match { it?.startsWith("build properties failed:") == true }) }
        verify(exactly = 1) { XLog.w("Common", match { it?.startsWith("Ali MiPush bridge failed:") == true }) }
        verify(exactly = 1) { XLog.w("Common", match { it?.startsWith("MIUI class bridge failed:") == true }) }
    }

    @Test
    fun `property-free mode skips properties but keeps both bridge steps`() {
        val steps = mutableListOf<String>()
        val common = RecordingCommon(steps = steps, failingSteps = emptySet())

        val result = common.fakeWithoutPropertySpoofing(loadParam())

        assertTrue(result)
        assertEquals(listOf("ali_bridge", "class_bridge"), steps)
    }

    @Test
    fun `property-free mode reports failure when both remaining steps fail`() {
        val steps = mutableListOf<String>()
        val common = RecordingCommon(
            steps = steps,
            failingSteps = setOf("ali_bridge", "class_bridge"),
        )

        val result = common.fakeWithoutPropertySpoofing(loadParam())

        assertFalse(result)
        assertEquals(listOf("ali_bridge", "class_bridge"), steps)
    }

    @Test
    fun `property-free mode restores flag when delegated fake throws`() {
        val steps = mutableListOf<String>()
        val common = ThrowingOnceCommon(steps)

        org.junit.jupiter.api.Assertions.assertThrows(IllegalStateException::class.java) {
            common.fakeWithoutPropertySpoofing(loadParam())
        }
        val result = common.fake(loadParam())

        assertTrue(result)
        assertEquals(listOf("properties", "ali_bridge", "class_bridge"), steps)
    }

    private fun loadParam(): LoadParam {
        return LoadParam(
            packageName = "com.example.push",
            processName = "com.example.push",
            classLoader = checkNotNull(javaClass.classLoader),
        )
    }

    private class ThrowingOnceCommon(
        private val steps: MutableList<String>,
    ) : Common() {
        private var throwOnNextFake = true

        override fun fake(lpparam: LoadParam): Boolean {
            if (throwOnNextFake) {
                throwOnNextFake = false
                error("forced delegated fake failure")
            }
            return super.fake(lpparam)
        }

        override fun fakeBuildProperties() {
            steps += "properties"
        }

        override fun enableAliMiPushBridge(lpparam: LoadParam) {
            steps += "ali_bridge"
        }

        override fun fakeClass(lpparam: LoadParam) {
            steps += "class_bridge"
        }
    }

    private class RecordingCommon(
        private val steps: MutableList<String>,
        private val failingSteps: Set<String>,
    ) : Common() {
        override fun fakeBuildProperties() {
            record("properties")
        }

        override fun enableAliMiPushBridge(lpparam: LoadParam) {
            record("ali_bridge")
        }

        override fun fakeClass(lpparam: LoadParam) {
            record("class_bridge")
        }

        private fun record(step: String) {
            steps += step
            if (step in failingSteps) {
                error("forced failure: $step")
            }
        }
    }
}
