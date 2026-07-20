package io.github.magisk317.mipush.diagnostics

import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.diagnostics.DiagnosticTextSanitizer
import java.io.File

object DiagnosticFileSanitizer {
    private const val MAX_SANITIZE_FILE_BYTES = 20 * 1024 * 1024L

    fun sanitizeDirectory(
        root: File,
        maxFileBytes: Long = MAX_SANITIZE_FILE_BYTES,
        onWarning: (String) -> Unit = {},
    ) = DiagnosticTextSanitizer.sanitizeDirectory(
        root = root,
        sanitizeText = DefaultLogSanitizer::sanitizeUnbounded,
        maxFileBytes = maxFileBytes,
        onWarning = onWarning,
    )

    internal fun sanitizeTextLogFile(
        file: File,
        maxFileBytes: Long = MAX_SANITIZE_FILE_BYTES,
        onWarning: (String) -> Unit = {},
    ): Boolean = DiagnosticTextSanitizer.sanitizeTextLogFile(
        file = file,
        sanitizeText = DefaultLogSanitizer::sanitizeUnbounded,
        maxFileBytes = maxFileBytes,
        onWarning = onWarning,
    )

    internal fun shouldSanitizeLogFile(file: File): Boolean =
        DiagnosticTextSanitizer.shouldSanitizeLogFile(file)
}
