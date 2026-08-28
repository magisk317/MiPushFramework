package io.github.magisk317.mipush.main.viewmodel

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PassiveRootRequestContractTest {
    @Test
    fun `application detail only requests root for explicit zygisk writes`() {
        val source = readSource("ApplicationInfoViewModel.kt")
        val load = source.section("private fun loadZygiskState", "fun updateZygiskEnabledForApp")
        val update = source.section("fun updateZygiskEnabledForApp", "private fun loadDiagnostics")

        assertFalse(load.contains("requestRootAccess"))
        assertTrue(load.contains("refreshRootAccessIfGranted"))
        assertTrue(load.contains("else null"))
        assertTrue(update.contains("if (!permissionGateway.requestRootAccess())"))
    }

    @Test
    fun `zygisk page load only detects root and save short-circuits without it`() {
        val source = readSource("ZygiskConfigViewModel.kt")
        val load = source.section("fun load()", "fun togglePackage")
        val save = source.section("fun saveConfig", "\n}")

        assertFalse(load.contains("requestRootAccess"))
        assertTrue(load.contains("refreshRootAccessIfGranted"))
        assertTrue(save.contains("val granted = permissionGateway.requestRootAccess()"))
        assertTrue(save.contains("val saved = granted && settingsManager.saveZygiskConfig"))
    }

    private fun readSource(fileName: String): String {
        val relativePath = "io/github/magisk317/mipush/main/viewmodel/$fileName"
        val source = File("src/main/java/$relativePath")
        check(source.isFile) {
            "Source not found: $fileName at ${source.path}"
        }
        return source.readText()
    }

    private fun String.section(start: String, end: String): String {
        val startIndex = indexOf(start)
        require(startIndex >= 0) { "Missing section start: $start" }
        val endIndex = indexOf(end, startIndex + start.length)
        require(endIndex >= 0) { "Missing section end: $end" }
        return substring(startIndex, endIndex)
    }
}
