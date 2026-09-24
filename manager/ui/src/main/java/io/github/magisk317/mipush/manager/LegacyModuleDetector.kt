package io.github.magisk317.mipush.manager

import android.content.pm.PackageManager
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge

internal object LegacyModuleDetector {
    val INCOMPATIBLE_MODULE_PACKAGES = listOf(
        "com.nihility.mipush",
    )

    /**
     * Installation is intentionally enough to trigger the warning. The legacy
     * package must not be enabled in LSPosed/KernelSU to be considered unsafe:
     * keeping both package identities installed can make the active module
     * ambiguous and can leave stale hooks in the target processes.
     */
    fun findInstalledPackages(packageManager: PackageManager): List<String> =
        INCOMPATIBLE_MODULE_PACKAGES.filter { packageName ->
            try {
                PackageManagerCompatBridge.getPackageInfo(
                    packageManager = packageManager,
                    packageName = packageName,
                    flags = 0,
                )
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            } catch (_: SecurityException) {
                false
            }
        }

    fun isInstalled(packageManager: PackageManager): Boolean =
        findInstalledPackages(packageManager).isNotEmpty()
}
