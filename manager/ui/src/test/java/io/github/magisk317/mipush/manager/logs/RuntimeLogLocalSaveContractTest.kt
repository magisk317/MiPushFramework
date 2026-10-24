package io.github.magisk317.mipush.manager.logs

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class RuntimeLogLocalSaveContractTest {
    @Test
    fun `settings diagnostics save through SAF without a share chooser`() {
        val source = source("io/github/magisk317/mipush/feature/main/subpage/SettingsPageSections.kt")

        assertTrue(source.contains("Toast.makeText(context, R.string.runtime_log_exporting, Toast.LENGTH_SHORT).show()"))
        assertTrue(source.contains("Toast.makeText(context, message, Toast.LENGTH_LONG).show()"))
        assertTrue(source.contains("ActivityResultContracts.CreateDocument(\"application/zip\")"))
        assertTrue(source.contains("viewModel.saveRuntimeLogBundle(context, destination)"))
        assertFalse(source.contains("Intent.createChooser"))
        assertFalse(source.contains("buildRuntimeLogShareIntent"))

        val viewModel = source("io/github/magisk317/mipush/main/viewmodel/SettingsViewModel.kt")
        assertTrue(viewModel.contains("runtimeLogExportScope.launch"))
        assertTrue(viewModel.contains("SupervisorJob() + Dispatchers.IO"))
        assertTrue(viewModel.contains("save_started"))
        assertTrue(viewModel.contains("save_finished success="))
        assertTrue(viewModel.contains("openOutputStream(destination, \"wt\")"))
        assertTrue(viewModel.contains("log_export_destination_empty"))
    }

    @Test
    fun `remote log export streams binder archive into the chosen destination`() {
        val source = source("io/github/magisk317/mipush/manager/remote/RemoteManagerLogGateway.kt")

        val writePath = source
            .substringAfter("override suspend fun writeLogBundle")
            .substringBefore("/**\n     * Legacy archive-path fallback")
        assertTrue(writePath.contains("mergeRuntimeZipWithManagerLogs(context, source.inputStream(), destination)"))
        assertTrue(writePath.contains("merged.bytesWritten <= 0L"))
        assertFalse(writePath.contains(".runtime-export-"))
    }

    @Test
    fun `an unreachable runtime still exports a manager-local bundle`() {
        val source = source("io/github/magisk317/mipush/manager/remote/RemoteManagerLogGateway.kt")

        val buildPath = source
            .substringAfter("override suspend fun buildLogBundle")
            .substringBefore("override suspend fun writeLogBundle")
        val unavailableBranch = buildPath.substringAfter("is LogExportReadResult.Unavailable ->")
        assertTrue(unavailableBranch.contains("writeManagerLocalBundle(context, output, note)"))
        assertFalse(unavailableBranch.contains("archivePath = null"))
        assertTrue(buildPath.contains("exported_manager_local_only"))

        val writePath = source
            .substringAfter("override suspend fun writeLogBundle")
            .substringBefore("private fun unavailableNote")
        assertTrue(writePath.contains("writeManagerLocalBundle(context, destination, note)"))

        // The block must be built from process-local state only: a Binder read or a root shell
        // here would fail in exactly the situation the fallback exists for.
        assertTrue(source.contains("availability = client.availability.value"))
        assertTrue(source.contains("rootGrantCache = rootAccess?.cachedGrantState()"))
        assertFalse(source.substringAfter("private fun appendManagerEntries").contains("Shell.cmd"))

        val settings = source("io/github/magisk317/mipush/feature/main/subpage/SettingsPageSections.kt")
        assertTrue(settings.contains("&& !viewModel.exportFallsBackToLocalBundle()"))
        assertTrue(settings.contains("runtimeLogSavedLocalOnlyMessage"))
    }

    private fun source(relativePath: String): String {
        val source = File("src/main/java/$relativePath")
        check(source.isFile) { "source missing: ${source.absolutePath}" }
        return source.readText()
    }
}
