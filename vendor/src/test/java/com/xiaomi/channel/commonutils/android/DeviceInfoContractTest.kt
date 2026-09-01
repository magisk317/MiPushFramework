import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DeviceInfoContractTest {
    @Test
    fun `device identifier optional probes do not use warning producing JavaCalls entrypoints`() {
        val source = resolveSource().readText()

        assertFalse("JavaCalls.callMethod(" in source)
        assertFalse("JavaCalls.callStaticMethod(" in source)
        assertTrue("JavaCalls.callMethodOrThrow" in source)
        assertTrue("JavaCalls.callStaticMethodOrThrow" in source)
        assertTrue("NoSuchMethodException" in source)
        assertTrue("ClassNotFoundException" in source)
    }

    private fun resolveSource(): java.io.File =
        listOf(
            java.io.File("vendor/src/main/java/com/xiaomi/channel/commonutils/android/DeviceInfo.kt"),
            java.io.File("src/main/java/com/xiaomi/channel/commonutils/android/DeviceInfo.kt"),
        ).firstOrNull(java.io.File::isFile)
            ?: error("DeviceInfo.kt not found")
}
