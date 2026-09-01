package io.github.magisk317.mipush.service

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BuildConfigurationReferenceTest {
    @Test
    fun `shrink and detekt configuration reference the live registration recorder`() {
        val projectRoot = File("../..")
        val applicationRules = File(projectRoot, "xmsf/proguard-rules.pro").readText()
        val shellRules = File(projectRoot, "xmsf/shell/proguard-rules.pro").readText()
        val detekt = File(projectRoot, "config/detekt/detekt.yml").readText()

        assertTrue(applicationRules.contains("io.github.magisk317.mipush.service.RegisterRecorder"))
        assertTrue(shellRules.contains("io.github.magisk317.mipush.service.RegisterRecorder"))
        assertTrue(detekt.contains("**/RegisterRecorder.kt"))
        assertFalse(applicationRules.contains("RegistrationRecorder"))
        assertFalse(shellRules.contains("RegistrationRecorder"))
        assertFalse(detekt.contains("**/RegistrationRecorder.kt"))
    }
}
