package io.github.magisk317.mipush.hook.securitycore

object SecurityCoreXSpaceMiPushPolicy {
    internal const val MIPUSH_MODULE_PACKAGE = "io.github.magisk317.mipush"

    private val ignoredPackages = setOf(
        "android",
        "com.miui.securitycore",
        "com.xiaomi.xmsf",
    )

    internal fun decide(
        packageName: String?,
        originalRequired: Boolean?,
        dualAppEnabled: Boolean = false,
    ): SecurityCoreXSpaceMiPushDecision {
        val normalizedPackageName = packageName?.trim().orEmpty()
        if (originalRequired == true) {
            return SecurityCoreXSpaceMiPushDecision(forceRequired = false, reason = "already_required")
        }
        if (!dualAppEnabled) {
            return SecurityCoreXSpaceMiPushDecision(forceRequired = false, reason = "dual_app_disabled")
        }
        if (normalizedPackageName.isBlank()) {
            return SecurityCoreXSpaceMiPushDecision(forceRequired = false, reason = "blank_package")
        }
        if (normalizedPackageName in ignoredPackages) {
            return SecurityCoreXSpaceMiPushDecision(forceRequired = false, reason = "ignored_package")
        }
        if (normalizedPackageName != MIPUSH_MODULE_PACKAGE) {
            return SecurityCoreXSpaceMiPushDecision(forceRequired = false, reason = "not_mipush_module")
        }
        return SecurityCoreXSpaceMiPushDecision(forceRequired = true, reason = "force_xspace_xmsf_retention")
    }
}

data class SecurityCoreXSpaceMiPushDecision(
    val forceRequired: Boolean,
    val reason: String,
)
