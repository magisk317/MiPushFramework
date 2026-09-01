import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class OptionalReflectionContractTest {
    @Test
    fun `assemble push probes do not use warning producing JavaCalls entrypoints`() {
        val source = resolveSource("com/xiaomi/mipush/sdk/AssemblePushUtils.kt").readText()

        assertFalse("JavaCalls.callMethod(" in source)
        assertFalse("JavaCalls.callStaticMethod(" in source)
        assertFalse("JavaCalls.getStaticField(" in source)
        assertTrue("JavaCalls.callMethodOrThrow" in source)
        assertTrue("JavaCalls.callStaticMethodOrThrow" in source)
        assertTrue("NoSuchMethodException" in source)
        assertTrue("ClassNotFoundException" in source)
    }

    @Test
    fun `push manager factory treats missing third party manager as optional`() {
        val source = resolveSource("com/xiaomi/mipush/sdk/PushManagerFactory.kt").readText()

        assertFalse("JavaCalls.callStaticMethod(" in source)
        assertTrue("JavaCalls.callStaticMethodOrThrow" in source)
        assertTrue("NoSuchMethodException" in source)
        assertTrue("ClassNotFoundException" in source)
    }

    @Test
    fun `miui property probes use silent missing property fallback`() {
        val miuiSource = resolveSource("com/xiaomi/channel/commonutils/android/MIUIUtils.kt").readText()
        val propertiesSource = resolveSource("com/xiaomi/channel/commonutils/android/SystemProperties.kt").readText()

        assertFalse("JavaCalls.callStaticMethod(" in miuiSource)
        assertTrue("SystemProperties.get(str, \"\")" in miuiSource)
        assertTrue("NoSuchMethodException" in propertiesSource)
        assertTrue("ClassNotFoundException" in propertiesSource)
    }

    private fun resolveSource(relativePath: String): java.io.File =
        listOf(
            java.io.File("vendor/src/main/java/$relativePath"),
            java.io.File("src/main/java/$relativePath"),
        ).firstOrNull(java.io.File::isFile)
            ?: error("$relativePath not found")
}
