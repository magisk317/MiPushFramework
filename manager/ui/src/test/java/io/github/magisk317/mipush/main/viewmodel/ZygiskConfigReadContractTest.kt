package io.github.magisk317.mipush.main.viewmodel

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ZygiskConfigReadContractTest {
    @Test
    fun `remote gateway preserves unavailable config reads`() {
        val source = readSource(
            "io/github/magisk317/mipush/manager/remote/RemoteZygiskConfigGateway.kt",
        )
        val section = source

        assertTrue(section.contains("ZygiskConfigReadResult.Unavailable(\"runtime_unavailable\")"))
        assertTrue(section.contains("ZygiskConfigReadResult.Unavailable(result.details"))
        assertFalse(section.contains("return ZygiskConfig()"))
    }

    @Test
    fun `remote gateway preserves unavailable module and scan reads`() {
        val source = readSource(
            "io/github/magisk317/mipush/manager/remote/RemoteZygiskConfigGateway.kt",
        )
        val section = source
        val module = section.section("override suspend fun isZygiskModuleEnabled", "override fun getZygiskConfigPath")
        val scan = section.substring(section.indexOf("override suspend fun scanZygiskPackages"))

        assertTrue(module.contains("ZygiskModuleReadResult.Unavailable(\"runtime_unavailable\")"))
        assertTrue(scan.contains("ZygiskPackageScanResult.Unavailable(\"runtime_unavailable\")"))
        assertFalse(module.contains("return false"))
        assertFalse(scan.contains("return \"\""))
    }

    @Test
    fun `view model never saves after an unavailable config read`() {
        val source = readSource(
            "io/github/magisk317/mipush/main/viewmodel/ZygiskConfigViewModel.kt",
        )
        val save = source.section("fun saveConfig", "\n    fun setProfile")

        assertTrue(save.contains("if (!_state.value.configReadAvailable)"))
        assertTrue(save.contains("as? ZygiskConfigReadResult.Available"))
        assertTrue(save.contains("return@withContext granted to false"))
    }

    @Test
    fun `view model does not replace unavailable config with an empty editable snapshot`() {
        val source = readSource(
            "io/github/magisk317/mipush/main/viewmodel/ZygiskConfigViewModel.kt",
        )

        assertTrue(source.contains("val availableConfig = (configResult as? ZygiskConfigReadResult.Available)?.config"))
        assertTrue(source.contains("} ?: nextState"))
        assertTrue(source.contains("if (!_state.value.configReadAvailable) return"))
        assertTrue(source.contains("if (_state.value.configReadAvailable)"))
    }

    @Test
    fun `zygisk config page disables every config editor when the read is unavailable`() {
        val source = readSource(
            "io/github/magisk317/mipush/feature/main/ZygiskConfigPage.kt",
        )

        assertTrue(source.contains("enabled = state.configReadAvailable"))
        assertTrue(source.contains("Switch(checked = observe, enabled = enabled"))
        assertFalse(source.contains("autoScan"))
    }

    @Test
    fun `view model places enabled packages first without reshuffling groups`() {
        val source = readSource(
            "io/github/magisk317/mipush/main/viewmodel/ZygiskConfigViewModel.kt",
        )

        assertTrue(source.contains("appsList.sortedByDescending { it.packageName in enabledPackages }"))
    }

    @Test
    fun `view model exposes module and scan failures instead of disabled or empty states`() {
        val source = readSource(
            "io/github/magisk317/mipush/main/viewmodel/ZygiskConfigViewModel.kt",
        )

        assertTrue(source.contains("zygiskStatusAvailable"))
        assertTrue(source.contains("zygiskStatusError"))
        assertTrue(source.contains("scanError"))
        assertTrue(source.contains("is ZygiskPackageScanResult.Unavailable"))
    }

    private fun readSource(relativePath: String): String {
        val source = File("src/main/java/$relativePath")
        check(source.isFile) {
            "Source not found: $relativePath at ${source.path}"
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
