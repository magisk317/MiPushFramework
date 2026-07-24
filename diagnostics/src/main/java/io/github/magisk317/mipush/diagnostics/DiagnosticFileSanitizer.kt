package io.github.magisk317.mipush.diagnostics

import io.github.magisk317.xposed.logging.DefaultLogSanitizer
import io.github.magisk317.xposed.diagnostics.DiagnosticTextSanitizer
import java.io.File

object DiagnosticFileSanitizer {
    fun sanitizeDirectory(
        root: File,
        maxFileBytes: Long = Long.MAX_VALUE,
        onWarning: (String) -> Unit = {},
        includeFile: (File) -> Boolean = { true },
        parallel: Boolean = true,
    ) = DiagnosticTextSanitizer.sanitizeDirectory(
        root = root,
        sanitizeText = DefaultLogSanitizer::sanitizeUnbounded,
        maxFileBytes = maxFileBytes,
        onWarning = onWarning,
        includeFile = includeFile,
        parallel = parallel,
    )

    internal fun sanitizeTextLogFile(
        file: File,
        maxFileBytes: Long = Long.MAX_VALUE,
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
