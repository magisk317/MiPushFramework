package io.github.magisk317.mipush.app.di

import io.github.magisk317.mipush.platform.support.BoundedShellResult

/**
 * Stable reason vocabulary for zygisk gateway failures.
 *
 * Reasons reach telemetry as an identifier, so raw stderr never leaks into them: a root probe
 * skip, a timeout, a missing config file or module script, a momentarily unavailable su daemon
 * and a generic failure are distinct states instead of one umbrella reason.
 */
internal object ZygiskFailure {
    private val MISSING_FILE_PATTERN =
        Regex("(?i)no such file|not exist|cannot open|can't open|does not exist")

    /**
     * Blank stderr on a non-zero exit: libsu could not run the command because the su daemon was
     * unusable at exec time. Worth one forced root probe plus retry.
     */
    fun isTransientExecFailure(result: BoundedShellResult): Boolean =
        !result.skipped && !result.timedOut && result.stderrText.isBlank()

    fun reason(result: BoundedShellResult): String = when {
        result.skipped -> "zygisk_root_missing"
        result.timedOut -> "zygisk_config_read_timeout"
        MISSING_FILE_PATTERN.containsMatchIn(result.stderrText) -> "zygisk_config_missing"
        result.stderrText.isNotBlank() -> "zygisk_config_read_failed"
        else -> "zygisk_root_exec_failed"
    }

    fun scanReason(result: BoundedShellResult): String = when {
        result.skipped -> "zygisk_root_missing"
        result.timedOut -> "zygisk_scan_timeout"
        MISSING_FILE_PATTERN.containsMatchIn(result.stderrText) -> "zygisk_module_not_installed"
        result.stderrText.isNotBlank() -> "zygisk_scan_failed"
        else -> "zygisk_root_exec_failed"
    }
}
