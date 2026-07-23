package io.github.magisk317.mipush.diagnostics

import android.content.Context
import io.mockk.mockk
import java.io.File
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DiagnosticArchiveTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun copyDirectory_appliesFilterAndPreservesPaths() {
        val source = File(tempDir, "source").apply { mkdirs() }
        File(source, "nested").mkdirs()
        File(source, "keep.jsonl").writeText("one")
        File(source, "nested/skip.txt").writeText("two")
        val target = File(tempDir, "target")

        DiagnosticArchive.copyDirectory(source, target, includeFile = { it.extension == "jsonl" })

        assertEquals("one", File(target, "keep.jsonl").readText())
        assertFalse(File(target, "nested/skip.txt").exists())
    }

    @Test
    fun clearDirectories_recreatesTargets() {
        val logs = File(tempDir, "logs").apply { mkdirs() }
        File(logs, "runtime.jsonl").writeText("entry")

        val result = DiagnosticArchive.clearDirectories(listOf("log" to logs))

        assertTrue(result.success)
        assertTrue(logs.isDirectory)
        assertTrue(logs.listFiles().isNullOrEmpty())
    }

    @Test
    fun clearDirectories_reportsDeleteFailure() {
        val logs = File(tempDir, "logs").apply { mkdirs() }
        File(logs, "runtime.jsonl").writeText("entry")

        val result = DiagnosticArchive.clearDirectories(
            targets = listOf("log" to logs),
            deletePath = { false },
        )

        assertFalse(result.success)
        assertEquals("log clear failed", result.details)
    }

    @Test
    fun buildBundle_producesShareableZipWhenNoLogsCollected() {
        val exportDir = File(tempDir, "export").apply { mkdirs() }

        val result = DiagnosticArchive.buildBundle(
            context = mockk<Context>(relaxed = true),
            timestamp = "fixed",
            exportDir = exportDir,
            exportFilePrefix = "logs_",
            stagingDirPrefix = ".tmp_",
            collect = { _, details -> details += "app log missing" },
        )

        val zip = result.file
        assertTrue(zip != null && zip.isFile && zip.length() > 0)
        assertEquals(File(exportDir, "logs_fixed.zip"), zip)
        assertTrue(result.details.contains("app log missing"))
        assertTrue(exportDir.listFiles().orEmpty().none { it.name.startsWith(".tmp_") })
    }

    @Test
    fun buildBundle_stopsWhenStaleStagingCannotBeDeleted() {
        val exportDir = File(tempDir, "export").apply { mkdirs() }
        File(exportDir, ".tmp_fixed").mkdirs()
        var collected = false

        val result = DiagnosticArchive.buildBundle(
            context = mockk<Context>(relaxed = true),
            timestamp = "fixed",
            exportDir = exportDir,
            exportFilePrefix = "logs_",
            stagingDirPrefix = ".tmp_",
            deletePath = { false },
            collect = { _, _ -> collected = true },
        )

        assertFalse(collected)
        assertEquals(null, result.file)
        assertTrue(result.details.startsWith("staging cleanup failed:"))
    }
}
