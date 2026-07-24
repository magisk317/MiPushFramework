package io.github.magisk317.mipush.diagnostics

import java.io.File
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class DiagnosticFileSanitizerTest {
    @TempDir
    lateinit var tempDir: File

    @Test
    fun sanitizeDirectory_redactsSharedSensitivePatternsInTextLogs() {
        val jsonl = File(tempDir, "runtime.2026-07-15.jsonl").apply {
            writeText(
                """{"message":"token=plain phone=13800138000","sender":"13800138000","code":"123456"}""",
            )
        }
        val log = File(tempDir, "modules.log").apply {
            writeText("Bearer abcdef1234567890\nimei=123456789012345\n")
        }
        val txt = File(tempDir, "raw.txt").apply {
            writeText("token=plain")
        }
        val props = File(tempDir, "props").apply {
            writeText("deviceId=abcdef1234567890")
        }

        DiagnosticFileSanitizer.sanitizeDirectory(tempDir)

        val combined = listOf(jsonl, log, txt, props).joinToString("\n") { it.readText() }
        assertFalse(combined.contains("plain"))
        assertFalse(combined.contains("13800138000"))
        assertFalse(combined.contains("123456789012345"))
        assertFalse(combined.contains("abcdef1234567890"))
        assertTrue(combined.contains("token=***"))
        assertTrue(combined.contains("Bearer ***"))
        assertTrue(combined.contains("deviceId=***"))
        assertTrue(combined.contains("payload[len="))
        assertTrue(combined.contains("sender[len="))
        assertTrue(combined.contains("code[len="))
    }

    @Test
    fun sanitizeDirectory_skipsUnsupportedAndKeepsFullOversizedTextFiles() {
        val binary = File(tempDir, "image.png").apply { writeText("token=plain") }
        val oversized = File(tempDir, "large.log").apply {
            writeText("token=plain-keep-full-content")
        }
        val warnings = mutableListOf<String>()

        DiagnosticFileSanitizer.sanitizeDirectory(
            tempDir,
            maxFileBytes = 4L,
            onWarning = warnings::add,
        )

        assertTrue(binary.readText().contains("token=plain"))
        assertTrue(oversized.exists())
        val text = oversized.readText()
        assertTrue(text.contains("keep-full-content") || text.contains("token="))
        assertTrue(warnings.any { it.contains("without truncation") })
    }
}
