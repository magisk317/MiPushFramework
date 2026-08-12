package io.github.magisk317.mipush.app

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ConfigCenterContractTest {
    @Test
    fun `configuration center keeps preference ownership explicit`() {
        val source = readSource("io/github/magisk317/mipush/app/ConfigCenter.kt")

        assertTrue(source.contains("private val preferenceRepository: PreferenceRepository"))
        assertFalse(source.contains("constructor()"))
        assertFalse(source.contains("PreferenceRepository()"))
    }

    private fun readSource(relativePath: String): String {
        val candidates = listOf(
            File("src/main/java/$relativePath"),
            File("xmsf/src/main/java/$relativePath"),
        )
        return candidates.firstOrNull(File::isFile)?.readText()
            ?: error("Source not found: $relativePath from ${File(".").absolutePath}")
    }
}
