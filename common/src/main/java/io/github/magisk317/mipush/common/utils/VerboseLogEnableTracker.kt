package io.github.magisk317.mipush.common.utils

/**
 * Tracks when verbose logging was last toggled on.
 * Used to enforce a minimum interval between enabling verbose logging
 * and exporting logs, ensuring users have time to reproduce the issue.
 *
 * Not persisted — resets on app restart. If verbose logging was already
 * enabled before restart (from SharedPreferences), the timestamp is null
 * and the time check is skipped, trusting long-running verbose mode.
 */
object VerboseLogEnableTracker {

    private const val MIN_INTERVAL_MS = 60_000L

    @Volatile
    private var enableTimestamp: Long? = null

    fun onVerboseLogToggled(enabled: Boolean) {
        enableTimestamp = if (enabled) System.currentTimeMillis() else null
    }

    fun shouldBlockExport(): String? {
        val ts = enableTimestamp ?: return null
        val elapsed = System.currentTimeMillis() - ts
        return if (elapsed < MIN_INTERVAL_MS) {
            "uikit_log_export_need_reproduce"
        } else {
            null
        }
    }

    /**
     * Pre-export validation. Returns a string resource key if the export
     * should be blocked, or null if all checks pass.
     *
     * @param verboseLogEnabled current state of the verbose log toggle
     * @param hasRootAccess supplier that returns whether root access is available
     */
    fun checkPreExport(
        verboseLogEnabled: Boolean,
        hasRootAccess: () -> Boolean,
    ): String? {
        if (!hasRootAccess()) {
            return "uikit_log_export_need_root"
        }
        if (!verboseLogEnabled) {
            return "uikit_log_export_need_verbose_log"
        }
        return shouldBlockExport()
    }
}
