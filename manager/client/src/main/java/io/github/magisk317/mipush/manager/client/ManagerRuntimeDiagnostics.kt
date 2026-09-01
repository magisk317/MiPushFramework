package io.github.magisk317.mipush.manager.client

private val SAFE_RUNTIME_EXCEPTION_REASON = Regex("[a-z][a-z0-9_]{0,95}")

internal fun runtimeExceptionDiagnosticReason(error: RuntimeException): String =
    error.message
        ?.trim()
        ?.takeIf(SAFE_RUNTIME_EXCEPTION_REASON::matches)
        ?: "redacted"
