package io.github.magisk317.mipush.manager

import android.content.pm.PackageManager
import io.github.magisk317.mipush.common.compat.PackageManagerCompatBridge

internal object LegacyModuleDetector {
    const val LEGACY_MODULE_PACKAGE_NAME = "com.nihility.mipush"

    /**
     * Installation is intentionally enough to trigger the warning. The legacy
     * package must not be enabled in LSPosed/KernelSU to be considered unsafe:
     * keeping both package identities installed can make the active module
     * ambiguous and can leave stale hooks in the target processes.
     */
    fun isInstalled(packageManager: PackageManager): Boolean = try {
        PackageManagerCompatBridge.getPackageInfo(
            packageManager = packageManager,
            packageName = LEGACY_MODULE_PACKAGE_NAME,
            flags = 0,
        )
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    } catch (_: SecurityException) {
        false
    }
}
