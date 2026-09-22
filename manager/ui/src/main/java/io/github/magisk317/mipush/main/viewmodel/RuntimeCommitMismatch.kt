package io.github.magisk317.mipush.main.viewmodel

import io.github.magisk317.mipush.manager.client.ManagerRuntimeAvailability

/**
 * Module (mipush APK) vs runtime (xmsf APK) build mismatch.
 *
 * The Xposed adaptation plane resolves target classes by string name from the xmsf process, so a
 * module and a runtime built from different commits can silently degrade (hook injection fails,
 * mock replay receipts post under the wrong identity and duplicate notifications appear).
 */
data class RuntimeCommitMismatch(
    val moduleCommit: String,
    val runtimeCommit: String,
)

/**
 * Returns the mismatch when the bound runtime reports a git commit that differs from the commit
 * this manager/module APK was built from. Older runtimes that do not report a commit yield null.
 */
internal fun ManagerRuntimeAvailability.runtimeCommitMismatch(
    moduleCommit: String,
): RuntimeCommitMismatch? {
    if (this !is ManagerRuntimeAvailability.Available) return null
    val runtimeCommit = handshake.runtimeCommit
        ?.takeIf { it.isNotBlank() && it != "unknown" }
        ?: return null
    val module = moduleCommit.takeIf { it.isNotBlank() && it != "unknown" } ?: return null
    return if (runtimeCommit != module) {
        RuntimeCommitMismatch(moduleCommit = module, runtimeCommit = runtimeCommit)
    } else {
        null
    }
}
