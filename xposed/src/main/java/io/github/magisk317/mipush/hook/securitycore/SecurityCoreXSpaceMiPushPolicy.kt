package io.github.magisk317.mipush.hook.securitycore

object SecurityCoreXSpaceMiPushPolicy {
    private val ignoredPackages = setOf(
        "android",
        "com.miui.securitycore",
        "com.xiaomi.xmsf",
    )

    internal fun decide(packageName: String?, originalRequired: Boolean?): SecurityCoreXSpaceMiPushDecision {
        val normalizedPackageName = packageName?.trim().orEmpty()
        if (originalRequired == true) {
            return SecurityCoreXSpaceMiPushDecision(forceRequired = false, reason = "already_required")
        }
        if (normalizedPackageName.isBlank()) {
            return SecurityCoreXSpaceMiPushDecision(forceRequired = false, reason = "blank_package")
        }
        if (normalizedPackageName in ignoredPackages) {
            return SecurityCoreXSpaceMiPushDecision(forceRequired = false, reason = "ignored_package")
        }
        return SecurityCoreXSpaceMiPushDecision(forceRequired = true, reason = "force_xspace_xmsf_retention")
    }
}

data class SecurityCoreXSpaceMiPushDecision(
    val forceRequired: Boolean,
    val reason: String,
)
